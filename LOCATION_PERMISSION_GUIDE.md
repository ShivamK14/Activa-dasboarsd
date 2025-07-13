# Location Permission Guide - Activa Dashboard

## The Issue
You're seeing this error in the logs:
```
Permission violation - getConfiguredNetworks not allowed for uid=10896, packageName=com.example.activadasboard, reason=java.lang.SecurityException: UID 10896 has no location permission
```

## Why This Happens
Starting with Android 10, Google requires **Location Permission** to access WiFi network information. This is a system requirement, not something the app chooses to require.

## How to Fix It

### Method 1: Through the App (Recommended)
1. **Open the Activa Dashboard app**
2. **Look for a permission request dialog** that should appear automatically
3. **Tap "Grant Permission"** when prompted
4. **Follow the system dialog** to grant location permission

### Method 2: Through Android Settings
1. **Go to Settings** on your phone
2. **Tap "Apps"** or "Application Manager"
3. **Find "Activa Dashboard"** in the list
4. **Tap "Permissions"**
5. **Enable "Location"** permission
6. **Restart the app**

### Method 3: Through Location Settings
1. **Go to Settings** > **Location**
2. **Make sure Location is turned ON**
3. **Tap "App permissions"** or "App-level permissions"
4. **Find "Activa Dashboard"**
5. **Set to "Allow all the time"** or "Allow while using app"

## What the App Does Now

The updated app will:

✅ **Show a permission request dialog** when you first open it  
✅ **Display permission status** in the Settings page  
✅ **Continue working** even without location permission (with limited functionality)  
✅ **Provide clear error messages** when permission is needed  

## Why Location Permission is Needed

The app needs location permission to:
- **Check existing WiFi networks** (required by Android)
- **Connect to your ESP8266 dashboard** automatically
- **Manage WiFi connections** properly

## What Happens Without Location Permission

Without location permission, the app will:
- ⚠️ **Show warnings in logs** (this is normal)
- ⚠️ **May not auto-connect** to ESP8266 network
- ✅ **Still work** with manual WiFi connection
- ✅ **Display data** once connected

## After Granting Permission

Once you grant location permission:
- ✅ **No more permission errors** in logs
- ✅ **Automatic ESP8266 connection** will work
- ✅ **Better WiFi management**
- ✅ **Improved connection stability**

## Troubleshooting

If you still see permission errors after granting permission:

1. **Restart the app** completely
2. **Check if permission is actually granted** in Settings
3. **Try Method 2 or 3** above
4. **Restart your phone** if needed

## Important Notes

- **Location permission is required by Android**, not the app
- **The app doesn't track your location** - it only needs permission to access WiFi
- **This is a one-time setup** - you won't need to do this again
- **The app will work without it**, but with limited functionality

## Still Having Issues?

If you continue to have problems:
1. **Check the Settings page** in the app for permission status
2. **Look for error messages** in the app UI
3. **Restart the app** after granting permissions
4. **Contact support** if the issue persists 