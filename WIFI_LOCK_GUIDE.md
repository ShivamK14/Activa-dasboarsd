# WiFi Lock Guide - Activa Dashboard

## The Issue
You're unable to connect to other WiFi networks because the app is "locking" your phone to the ESP8266 dashboard network.

## Why This Happens
The Activa Dashboard app is designed to maintain a stable connection with your ESP8266 dashboard. To do this, it uses several mechanisms:

1. **WiFi Locks**: Keeps WiFi active and connected to the ESP8266
2. **Automatic Reconnection**: Automatically reconnects if the connection is lost
3. **Network Monitoring**: Watches for network changes and tries to reconnect

## The Solution: WiFi Lock Control

The app now includes a **WiFi Lock Switch** in the Settings page that allows you to control this behavior.

### How to Connect to Other WiFi Networks

#### Method 1: Using the App Settings (Recommended)
1. **Open the Activa Dashboard app**
2. **Go to Settings** (bottom navigation)
3. **Scroll down to "WiFi Connection Optimization"** section
4. **Turn OFF the "Lock WiFi to ESP8266 Dashboard" switch**
5. **You'll see a message**: "WiFi lock disabled - you can now connect to other networks"
6. **Now you can connect to any WiFi network** through your phone's WiFi settings

#### Method 2: Temporary Disconnection
1. **Turn off your ESP8266 device** (unplug power)
2. **Wait a few seconds**
3. **Connect to your desired WiFi network**
4. **Turn ESP8266 back on** when you want to use the dashboard

## Understanding the WiFi Lock

### When WiFi Lock is ENABLED (Default)
- ✅ **Automatic ESP8266 connection** maintained
- ✅ **Stable dashboard connection** even when screen is off
- ✅ **Automatic reconnection** if connection is lost
- ⚠️ **Cannot connect to other WiFi networks** easily

### When WiFi Lock is DISABLED
- ✅ **Can connect to any WiFi network** freely
- ✅ **Manual control** over WiFi connections
- ⚠️ **No automatic ESP8266 connection**
- ⚠️ **Dashboard may disconnect** when screen goes off

## When to Use Each Mode

### Use WiFi Lock ENABLED when:
- **Using the dashboard actively**
- **Want automatic connection**
- **Don't need internet access** on your phone
- **Want stable dashboard connection**

### Use WiFi Lock DISABLED when:
- **Need to connect to other WiFi networks**
- **Want to browse the internet**
- **Using other apps that need internet**
- **Not actively using the dashboard**

## How to Switch Between Modes

### To Connect to Other WiFi:
1. **Disable WiFi Lock** in app settings
2. **Go to phone WiFi settings**
3. **Connect to desired network**
4. **Use internet normally**

### To Return to Dashboard:
1. **Re-enable WiFi Lock** in app settings
2. **Or manually connect** to "Activa_Dashboard" network
3. **Dashboard will reconnect automatically**

## Troubleshooting

### Can't Connect to Other WiFi Even After Disabling Lock
1. **Restart the app** completely
2. **Check if the switch is actually OFF**
3. **Try restarting your phone**
4. **Check if ESP8266 is still powered on** (turn it off temporarily)

### Dashboard Won't Reconnect After Re-enabling Lock
1. **Make sure ESP8266 is powered on**
2. **Check if you're connected to "Activa_Dashboard" network**
3. **Wait a few seconds** for automatic reconnection
4. **Restart the app** if needed

### App Keeps Reconnecting to ESP8266
1. **Make sure WiFi Lock is DISABLED**
2. **Check the Settings page** for current status
3. **Restart the app** to reset the connection state

## Technical Details

The WiFi Lock feature controls:
- **WiFi Lock acquisition/release**
- **Automatic reconnection attempts**
- **Network state monitoring**
- **Screen-off connection maintenance**

When disabled, the app:
- **Releases all WiFi locks**
- **Stops automatic reconnection**
- **Allows manual WiFi control**
- **Still monitors connection** (but doesn't force reconnection)

## Important Notes

- **WiFi Lock is enabled by default** for optimal dashboard experience
- **You can change this setting anytime** in the app
- **The setting is not permanent** - you can switch back and forth
- **No data is lost** when switching between modes
- **The app remembers your preference** during the session

## Quick Reference

| Action | WiFi Lock ON | WiFi Lock OFF |
|--------|-------------|---------------|
| Connect to other WiFi | ❌ Difficult | ✅ Easy |
| Automatic ESP8266 connection | ✅ Yes | ❌ No |
| Internet access | ❌ Limited | ✅ Full |
| Dashboard stability | ✅ High | ⚠️ Manual |
| Battery usage | ⚠️ Higher | ✅ Lower |

This feature gives you full control over your WiFi connections while maintaining the dashboard functionality when needed. 