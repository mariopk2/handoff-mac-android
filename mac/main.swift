// ClipBridge за macOS — menu bar приложение, което приема/подава текстов клипборд
// към Android телефон в същата Wi‑Fi мрежа.
//
// HTTP API (порт 8765, всяка заявка изисква хедър X-Token):
//   POST /clip  — тялото (UTF‑8 текст) отива в клипборда на Mac
//   GET  /clip  — връща текущия текст от клипборда на Mac
//   GET  /ping  — проверка на връзката

import Cocoa
import Network
import ServiceManagement

let kPort: UInt16 = 8765
let kServiceType = "_clipbridge._tcp"

// MARK: - HTTP

struct Request {
    let method: String
    let path: String
    let headers: [String: String]
    let body: Data

    /// Връща nil, докато заявката не е получена изцяло.
    static func parse(_ buf: Data) -> Request? {
        guard let sep = buf.range(of: Data("\r\n\r\n".utf8)) else { return nil }
        let headText = String(decoding: buf[buf.startIndex..<sep.lowerBound], as: UTF8.self)
        var lines = headText.components(separatedBy: "\r\n")
        guard !lines.isEmpty else { return nil }
        let first = lines.removeFirst().split(separator: " ")
        guard first.count >= 2 else { return nil }

        var headers: [String: String] = [:]
        for line in lines {
            guard let i = line.firstIndex(of: ":") else { continue }
            let key = line[..<i].trimmingCharacters(in: .whitespaces).lowercased()
            let value = line[line.index(after: i)...].trimmingCharacters(in: .whitespaces)
            headers[key] = value
        }

        let length = Int(headers["content-length"] ?? "0") ?? 0
        let bodyStart = sep.upperBound
        guard buf.endIndex - bodyStart >= length else { return nil }
        let body = buf.subdata(in: bodyStart..<(bodyStart + length))
        return Request(method: String(first[0]), path: String(first[1]), headers: headers, body: body)
    }
}

final class Server {
    var token: String
    var onReceived: (() -> Void)?
    var onServed: (() -> Void)?
    private var listener: NWListener?

    init(token: String) { self.token = token }

    func start() throws {
        let params = NWParameters.tcp
        params.allowLocalEndpointReuse = true
        let l = try NWListener(using: params, on: NWEndpoint.Port(rawValue: kPort)!)
        // Bonjour — телефонът намира Mac-а сам, без да въвеждаш IP.
        l.service = NWListener.Service(name: nil, type: kServiceType)
        l.newConnectionHandler = { [weak self] conn in self?.accept(conn) }
        l.start(queue: .main)
        listener = l
    }

    private func accept(_ conn: NWConnection) {
        conn.start(queue: .main)
        read(conn, buffer: Data())
    }

    private func read(_ conn: NWConnection, buffer: Data) {
        conn.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, isComplete, error in
            guard let self = self else { return }
            var buf = buffer
            if let data = data { buf.append(data) }
            if let req = Request.parse(buf) {
                self.respond(conn, req)
            } else if isComplete || error != nil || buf.count > 20_000_000 {
                conn.cancel()
            } else {
                self.read(conn, buffer: buf)
            }
        }
    }

    private func respond(_ conn: NWConnection, _ req: Request) {
        var status = "200 OK"
        var body = Data()

        if req.headers["x-token"] != token {
            status = "401 Unauthorized"
            body = Data("bad token".utf8)
        } else if req.method == "POST" && req.path == "/clip" {
            let text = String(decoding: req.body, as: UTF8.self)
            let pb = NSPasteboard.general
            pb.clearContents()
            pb.setString(text, forType: .string)
            body = Data("ok".utf8)
            onReceived?()
        } else if req.method == "GET" && req.path == "/clip" {
            body = Data((NSPasteboard.general.string(forType: .string) ?? "").utf8)
            onServed?()
        } else if req.path == "/ping" {
            body = Data("ClipBridge".utf8)
        } else {
            status = "404 Not Found"
        }

        let head = "HTTP/1.1 \(status)\r\n"
            + "Content-Type: text/plain; charset=utf-8\r\n"
            + "Content-Length: \(body.count)\r\n"
            + "Connection: close\r\n\r\n"
        var out = Data(head.utf8)
        out.append(body)
        conn.send(content: out, completion: .contentProcessed { _ in conn.cancel() })
    }
}

// MARK: - Помощни

func localIPv4Addresses() -> [String] {
    var result: [String] = []
    var ifaddr: UnsafeMutablePointer<ifaddrs>?
    guard getifaddrs(&ifaddr) == 0, let first = ifaddr else { return [] }
    defer { freeifaddrs(ifaddr) }
    for ptr in sequence(first: first, next: { $0.pointee.ifa_next }) {
        let ifa = ptr.pointee
        guard let addr = ifa.ifa_addr, addr.pointee.sa_family == UInt8(AF_INET) else { continue }
        guard String(cString: ifa.ifa_name).hasPrefix("en") else { continue }
        var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
        if getnameinfo(addr, socklen_t(addr.pointee.sa_len), &host, socklen_t(host.count),
                       nil, 0, NI_NUMERICHOST) == 0 {
            result.append(String(cString: host))
        }
    }
    return result
}

func newToken() -> String {
    let chars = Array("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")
    return String((0..<6).map { _ in chars.randomElement()! })
}

// MARK: - Menu bar

final class AppDelegate: NSObject, NSApplicationDelegate, NSMenuDelegate {
    private var item: NSStatusItem!
    private var server: Server!
    private var running = false

    private var token: String {
        get {
            if let t = UserDefaults.standard.string(forKey: "token") { return t }
            let t = newToken()
            UserDefaults.standard.set(t, forKey: "token")
            return t
        }
        set { UserDefaults.standard.set(newValue, forKey: "token") }
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        item = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        setIcon("doc.on.clipboard")

        let menu = NSMenu()
        menu.delegate = self
        item.menu = menu

        server = Server(token: token)
        server.onReceived = { [weak self] in self?.flash("arrow.down.circle.fill") }
        server.onServed = { [weak self] in self?.flash("arrow.up.circle.fill") }
        do {
            try server.start()
            running = true
        } catch {
            let alert = NSAlert()
            alert.messageText = "ClipBridge не може да стартира"
            alert.informativeText = "Порт \(kPort) вероятно е зает.\n\(error)"
            alert.runModal()
        }
    }

    // Менюто се построява наново при всяко отваряне (IP-то може да се е сменило).
    func menuNeedsUpdate(_ menu: NSMenu) {
        menu.removeAllItems()

        let status = NSMenuItem(title: running ? "ClipBridge работи (порт \(kPort))" : "ClipBridge НЕ работи",
                                action: nil, keyEquivalent: "")
        status.isEnabled = false
        menu.addItem(status)

        let ips = localIPv4Addresses()
        let ipItem = NSMenuItem(title: "IP: " + (ips.isEmpty ? "няма мрежа" : ips.joined(separator: ", ")),
                                action: nil, keyEquivalent: "")
        ipItem.isEnabled = false
        menu.addItem(ipItem)

        let codeItem = NSMenuItem(title: "Код за телефона: \(token)", action: nil, keyEquivalent: "")
        codeItem.isEnabled = false
        menu.addItem(codeItem)

        menu.addItem(.separator())
        menu.addItem(withTitle: "Генерирай нов код", action: #selector(regenerate), keyEquivalent: "")
            .target = self

        let login = NSMenuItem(title: "Стартирай при вход", action: #selector(toggleLogin), keyEquivalent: "")
        login.target = self
        login.state = SMAppService.mainApp.status == .enabled ? .on : .off
        menu.addItem(login)

        menu.addItem(.separator())
        menu.addItem(withTitle: "Изход", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
    }

    @objc private func regenerate() {
        let t = newToken()
        token = t
        server.token = t
    }

    @objc private func toggleLogin() {
        let svc = SMAppService.mainApp
        if svc.status == .enabled { try? svc.unregister() } else { try? svc.register() }
    }

    private func setIcon(_ symbol: String) {
        let img = NSImage(systemSymbolName: symbol, accessibilityDescription: "ClipBridge")
        img?.isTemplate = true
        item.button?.image = img
    }

    private func flash(_ symbol: String) {
        setIcon(symbol)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { [weak self] in
            self?.setIcon("doc.on.clipboard")
        }
    }
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.accessory)
app.run()
