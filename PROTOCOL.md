# Message Relay protocol, version 1

How Message Relay sends messages to a server. Anything that can receive an HTTPS request can accept them.

## Sending a message

Each message is one request:

```
POST <webhook address>
Authorization: Bearer <key>
Content-Type: application/json; charset=utf-8
```

```json
{
  "version": 1,
  "app": "Discord",
  "package": "com.discord",
  "title": "Night Owls",
  "sender": "maya",
  "text": "run at 9?",
  "sentAt": 1795381200000
}
```

| Field | Meaning |
|---|---|
| `version` | This protocol's version. Later versions only add fields. |
| `app` | The name of the app the message came from, as shown on the phone. |
| `package` | That app's Android package name, which doesn't change with the phone's language. |
| `title` | The chat's name: the other person in a one-to-one chat, or the group or channel. May be empty for apps that don't show one. |
| `sender` | Who wrote it, in a group chat. Empty in one-to-one chats and when unknown. |
| `text` | The message. Never empty. Whitespace is collapsed to single spaces. |
| `sentAt` | When it was sent, in milliseconds since 1970 UTC, from the phone's clock. Absent when the notification doesn't give the message's own time. |

Servers should ignore fields they don't recognise.

## Responses

| Status | What Message Relay does |
|---|---|
| 2xx | Accepted. The message is removed from the phone. |
| 401 | Stops sending and keeps the message, until the key is changed. |
| Other 4xx | Drops that message (the server can't use it) and continues with the next. |
| 5xx, or no response | Keeps the message and retries later, backing off. |

The response body is ignored.

## Order and repeats

Messages are sent one at a time, oldest first. Each is sent once in normal use, but it may arrive again if the phone didn't receive the server's response (for example, if the connection dropped). Servers that care should treat two messages with the same `package`, `title`, `sender`, `text` and `sentAt` as one. Messages without `sentAt` can't be told apart from the same words sent again later; treating identical ones within a few minutes of each other as one works well.

Messages that can't be delivered within 30 days are dropped on the phone.

## Checking the connection

The app's **Save and check** button sends:

```
GET <webhook address>
Authorization: Bearer <key>
```

A 2xx response means the address and key work, and 401 means the key is wrong. Servers that don't support `GET` can return 405; the app then reports the status but still sends messages.

## Setup links

A server can fill in the app's address and key with a link the person opens on their phone:

```
messagerelay://setup?url=<webhook address>&key=<key>
```

Both values must be URL-encoded. The app saves them and checks the connection.

## What isn't sent

Only notifications from apps the person switched on are read. The person's own messages, group summaries ("3 new messages"), and ongoing notifications such as calls aren't sent.
