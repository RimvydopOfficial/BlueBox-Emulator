# 💙 BlueBox PC Emulator

**BlueBox PC Emulator** is an Android application powered by QEMU that allows users to create and run x86 virtual machines directly on their Android devices.

Developed by **Rimvydop**.

![Version](https://img.shields.io/badge/Version-0.1.0-blue)
![Platform](https://img.shields.io/badge/Platform-Android-green)
![Status](https://img.shields.io/badge/Status-Early_Release-orange)

## 🚀 Features

- 🖥️ Run compatible x86 operating systems on Android
- 💻 Create, edit, and manage virtual machines
- ⚙️ Configure CPU models and CPU cores
- 🧠 Adjust virtual machine RAM
- 💾 Support for ISO, QCOW2, and VHD images
- 🔌 IDE and VirtIO storage interface options
- 🖼️ Configurable graphics adapters
- 🔊 Virtual audio configuration
- 🌐 Virtual network configuration
- 🖱️ SDL2-powered virtual machine display
- 🌙 Modern dark-themed interface

## 📱 Requirements

- Android device with ARM64 support
- Sufficient available RAM and storage
- Android version compatible with the APK
- Compatible operating system image

**Note:** BlueBox uses software emulation for x86 guests on ARM64 devices. Performance depends on your hardware and VM configuration.

## 📥 Download & Installation

1. Open the repository's **Releases** section.
2. Download the latest BlueBox APK.
3. Allow APK installation from your browser or file manager if Android requests permission.
4. Install and launch BlueBox.

## 🛠️ How to Use

1. Open BlueBox.
2. Select **Create VM**.
3. Enter a name for your virtual machine.
4. Choose your CPU, RAM, and machine settings.
5. Select a compatible virtual disk image or ISO.
6. Configure storage, graphics, audio, and networking.
7. Save your virtual machine.
8. Select **Start VM**.

## 💿 Supported Image Formats

| Format | Description |
|---|---|
| ISO | Operating system installation media |
| QCOW2 | QEMU virtual disk image |
| VHD | Virtual hard disk image |

Actual compatibility may vary depending on the image and configuration.

## ⚙️ Virtual Hardware

BlueBox provides configuration options for:

- CPU model and core count
- RAM allocation
- i440FX and Q35 machine types
- IDE and VirtIO disk interfaces
- Virtual graphics adapters
- Sound cards
- Network adapters

Some hardware configurations require guest operating system drivers or may have limited support in this early release.

## 🗺️ Roadmap

### v0.1.0 — Initial Release
- First public version
- Virtual machine creation and management
- QEMU engine integration
- Windows boot support
- Hardware configuration interface
- Dark-themed UI

### Future Updates
- Improved mouse and keyboard controls
- Better performance and stability
- Additional virtual hardware compatibility
- Improved fullscreen experience
- More virtual machine management features

Roadmap items are planned and may change.

## ⚠️ Known Limitations

- Some operating systems may fail to boot.
- Emulation can be slow on lower-end Android devices.
- VirtIO devices may require additional guest drivers.
- Some hardware selections may use compatibility fallbacks.
- BlueBox is still in early development.

## 📜 Open Source & Licensing

BlueBox uses third-party software, including **QEMU** and **SDL2**.

These components have their own open-source licenses and copyright notices. Their respective license terms must be followed when distributing BlueBox, including any applicable source-code obligations.

The license for BlueBox's original application code should be specified separately.

## 👨‍💻 Developer

**Rimvydop**

BlueBox PC Emulator — Version 0.1.0

---

⭐ If you enjoy BlueBox, consider starring the repository to support future development!
