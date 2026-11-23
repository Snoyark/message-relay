# Privacy policy for Message Relay

Last updated: November 23, 2026

Message Relay copies messages from your phone's notifications to a webhook address that you choose, normally a server you run. This policy describes what it reads, where that goes, and what it keeps.

## What the app reads

With your permission (Android's notification access), Android shows the app every notification on your phone. The app reads only notifications from the apps you switch on in it, and ignores all others without storing or sending anything from them.

From the notifications it does read, it takes each message's text, the sender's name, the chat or channel's name, the name of the app it came from, and when the message was sent.

## Where it goes

The app sends this information only to the webhook address you enter, authenticated with the key you enter. It isn't sent to the app's developer or to anyone else. The app contains no analytics, advertising, crash reporting or other third-party code, and makes no other network requests.

What happens to messages on your server is up to that server's software and its operator, which is normally you.

## What the app keeps on the phone

- The server address, key and your choice of apps, until you change them or uninstall the app.
- Messages not yet accepted by your server, for up to 30 days, after which they're deleted unsent.
- A short-lived record of recently sent messages (up to 3 days), so the same message isn't sent twice.

All of this stays in the app's private storage on your phone, is excluded from Android backups, and is deleted when you uninstall the app.

## Your choices

You can stop the app reading an app's notifications by switching it off, stop it reading any by removing its notification access in Android's settings, and delete everything it holds by uninstalling it.

## Contact

Questions about this policy: open an issue at https://github.com/Snoyark/message-relay/issues.
