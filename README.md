# ClipBridge — clipboard sharing between Pixel and Mac

Two small apps that communicate only over the local Wi-Fi network (no cloud, no accounts).

- **Phone → Mac:** copy text, pull down the notification panel, and tap the **"To Mac"** tile. The text is now in the Mac's clipboard.
- **Mac → Phone:** copy on the Mac and tap the **"From Mac"** tile on the phone.
- Alternatively, select text and choose **Share → ClipBridge**.

## 1. Mac

```bash
xcode-select --install        # only if you don't have Command Line Tools installed
cd mac
./build.sh
mv ClipBridge.app /Applications/ && open /Applications/ClipBridge.app
```

A clipboard icon appears in the menu bar. There, you can see the **code for the phone** and enable "Launch at login."
If macOS asks whether to allow incoming connections or access to the local network, click **Allow**.

## 2. Android (Pixel)

1. Open the `android` folder in **Android Studio** (File → Open).
2. Enable USB debugging on your Pixel, connect it via cable, and press **Run ▶**. 
Alternative: Build → Build APK(s), then send the APK to the phone using Quick Share.
3. Open ClipBridge on the phone:
- enter the code from the Mac menu; 
- tap "Find Mac automatically," then "Save and test"; 
- tap both buttons to add the tiles.

## Notes

- If the Mac's IP address changes, the phone finds it automatically (Bonjour). - Each request is verified using the code. If you change it in the Mac menu, enter the new code on your phone as well.
- The connection over the local network uses HTTP and is unencrypted. Do not use it on public Wi-Fi networks when handling passwords.
- If Android Studio suggests updating Gradle/AGP, you can safely accept the update.
