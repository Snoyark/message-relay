# Message Relay

An Android app that copies the messages your phone shows notifications for (texts, Discord, Messenger, or any app you choose) to a webhook you control. Nothing signs in to those services, and messages go only to the address you enter.

- Reads notifications only from the apps you switch on, and ignores the rest.
- For chat apps, sends each message separately with its sender, so group chats keep who wrote what. Your own messages are left out.
- Messages wait on the phone while the server can't be reached, and are sent once it can (for up to 30 days).
- Only incoming messages that show a notification are seen: not your replies, muted chats, or history from before you set it up.
- No libraries, analytics, ads or accounts. The app's only network traffic is to your webhook.

Servers receive a simple JSON request per message, described in [PROTOCOL.md](PROTOCOL.md). Writing a server for it takes a few lines in most web frameworks.

## Using it

1. Install the app (see Building below).
2. Enter the webhook address and key from your server, or open the server's setup link (`messagerelay://setup?...`) on the phone to fill them in. Tap **Save and check**.
3. Tap **Allow notification access**, read what's collected, agree, and turn access on.
4. Tap **Keep running in the background** and set Message Relay to **Unrestricted**, so Android doesn't pause it.
5. Under **Copy messages from**, switch on the apps you want.

## Building

Needs Java 17 and the Android SDK (both come with [Android Studio](https://developer.android.com/studio)).

```sh
./gradlew test assembleRelease
```

The app is at `app/build/outputs/apk/release/app-release.apk`.

**Signing.** Without a release key, builds are signed with your computer's Android debug key. That's fine for installing on your own phone, but Android only installs an update signed with the same key, so build updates on the same computer. To sign with a release key (needed for Google Play), create one and a `keystore.properties` file next to this README (both are ignored by git):

```sh
keytool -genkeypair -v -keystore release.jks -alias relay -keyalg RSA -keysize 4096 -validity 10000
```

```properties
storeFile=release.jks
storePassword=...
keyAlias=relay
keyPassword=...
```

Keep the key and its passwords backed up somewhere safe: without them you can't publish updates.

Google Play takes an App Bundle rather than an APK: `./gradlew bundleRelease` builds `app/build/outputs/bundle/release/app-release.aab`.

## Contributing

Issues and pull requests are welcome. Changes are merged by the maintainer after review.

## Privacy

See [PRIVACY.md](PRIVACY.md).

## License

MIT. See [LICENSE](LICENSE).
