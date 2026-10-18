# Community feature API contract
Base frontend /api, backend paths below. Envelope `{code,message,data}`; pagination uses `{content:[],totalElements,totalPages,number,size}` with `pageNo` (1-based input), `size` 1..50; response number 0-based. All mutating routes and all /me, /notifications, /messages + /post/{id}/interaction and /users/{username}/relationship require Bearer JWT. Public reads never trust username/actor headers.

## Comments and interaction
- GET /post/{id}/comments?pageNo=1&size=20 => page of `{id,postId,parentId,username,alias,avatar,content,createdAt}`. Flat chronological page; parentId null for roots, root id for replies. Public visible posts/comments only.
- POST /post/{id}/comments `{content: string(1..2000), parentId?: number}` => comment object. Replies normalize to the root comment; max one displayed reply level.
- DELETE /comments/{id} => null. Own comment only; deleting root deletes replies; counters remain correct.
- GET /post/{id}/interaction => `{liked,bookmarked,likes,bookmarks,comments}` (authenticated).
- POST or DELETE /post/{id}/like => same interaction snapshot. No body required; idempotent.
- POST or DELETE /post/{id}/bookmark => same interaction snapshot; idempotent.
- GET /me/bookmarks?pageNo=1&size=20 => page of post summary `{id,title,username,alias,avatar,createTime,likes,comments,collects}`; hidden/disabled-author posts excluded.
- Existing post detail now also returns `likes,comments,collects,top,essence,status,moderationReason` alongside existing fields. Public post status PUBLISHED only.
- GET /me/posts?pageNo=1&size=20 => own post summary page including `{status,moderationReason}`; includes PENDING/HIDDEN.
- GET /me/posts/{id} => own full post detail regardless of moderation state. Editing a nonpublic post puts it into PENDING; existing author PUT/DELETE /post/{id} retained.

## Follow
- GET /users/{username}/relationship => `{following,followers,followingCount}` (authenticated; following is viewer→target bool).
- POST /users/{username}/follow and DELETE same => relationship snapshot; idempotent; self follow rejected.
- GET /me/following and /me/followers?pageNo=1&size=20 => page of `{id,username,alias,avatar,bio}`. Disabled accounts excluded.

## Notifications
- GET /notifications?pageNo=1&size=20 => page `{id,type,actorUsername,actorAlias,postId,commentId,content,createdAt,read}`. type COMMENT/REPLY/LIKE/FOLLOW/MODERATION; content is plain text.
- GET /notifications/unread => `{count}`.
- POST /notifications/{id}/read and POST /notifications/read-all => null; own recipients only.

## Private messages
- GET /messages/conversations?pageNo=1&size=20 => page `{username,alias,avatar,lastContent,createdAt,unread}` for current user's counterparts only.
- GET /messages?username=other&pageNo=1&size=20 => page `{id,senderUsername,recipientUsername,content,createdAt,read}` newest-first. Does not mark automatically.
- POST /messages `{username: recipient,content: string(1..2000)}` => message object. No self/disabled recipients.
- POST /messages/read `{username: counterpart}` => null, marks only incoming messages from that counterpart read.
- GET /messages/unread => `{count}`.

Passwords/email/upload remain root agent API contract; forgot/reset public. Rate limits: comments30/min, relationship changes60/min, messages20/min per account; existing login/register limits unchanged.
