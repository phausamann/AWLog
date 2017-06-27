# AWLog
A basic, configurable sensor data logger for Android Wear devices

## Features
* Log accelerometer and gyroscope data with specified sampling rate
* Schedule recording intervals
* Trigger sampling on motion (kind of unreliable)

## Known issues
* Horrible UI (especially the interaction with the settings page is very user-unfriendly)
* Data can only be accessed via Bluetooth debug mode
* Massive battery drain (unfortunately, this problem is inherent to Android; it is not possible to record sensor data without the CPU running)
