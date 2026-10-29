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
- GET /me/posts/{id} => own full post detail regardless of moderation state. New posts and every author edit enter PENDING and require operator review before public display; editing clears the previous moderation reason. Existing author PUT/DELETE /post/{id} retained.

## Follow
- GET /users/{username}/relationship => `{following,followers,followingCount}` (authenticated; following is viewer→target bool).
- POST /users/{username}/follow and DELETE same => relationship snapshot; idempotent; self follow rejected.
- GET /me/following and /me/followers?pageNo=1&size=20 => page of `{id,username,alias,avatar,bio}`. Disabled accounts excluded.

## Content discovery
- GET /post/list?tab=essence&pageNo=1&size=10 => public post page filtered by the existing `essence` moderation flag. Same query/tag/author filters as latest/hot; pinned posts first, then newest; excludes unpublished posts and disabled authors.
- GET /me/feed?pageNo=1&size=10 => authenticated current account's followed-author posts, newest first (global pinning does not reorder the feed). Supports query/tag/author. Never accepts a caller-supplied follower identity; missing authentication is 401. Unfollowing removes the author's posts immediately.
- Post summaries/details carry `top` and `essence` booleans for public badges.

## Private drafts
- GET /me/drafts?pageNo=1&size=20 => page `{id,postId,title,excerpt,version,updatedAt}`. Current account only; never included in public post/search/tag APIs. Maximum 100 stored drafts per account.
- GET /me/drafts/{uuid} => `{id,postId,postVersion,title,content,tags,version,updatedAt}`. Own draft only; another account's draft is 404.
- PUT /me/drafts/{uuid} with `{title,content,tags,postId,postVersion,version}` saves a partial draft. Empty title/content are allowed; limits match posts. New UUID uses `version:0`; saved drafts return version 1 and increment on each save. Existing drafts require the last returned version; stale writes return 409 without replacing content.
- `postId`/`postVersion` are both null for a new topic, or the original owned post ID/version for an edit. This association and base post version cannot change on the same draft.
- DELETE /me/drafts/{uuid} with JSON `{version}` removes only the matching revision. A stale save with a nonzero version cannot recreate a removed draft.
- POST /me/drafts/{uuid}/publish with JSON `{version}` requires nonblank title/content, creates the post (or updates its owned original using `postVersion`) and removes the draft in one transaction. Existing moderation and tag handling apply. A repeated publish is 404 and cannot create a second post. A version conflict keeps the draft intact.
- Draft save/delete/publish lock the account before the draft/post to keep quota and concurrent writes consistent. All routes use the existing active JWT/session checks and `Cache-Control: no-store`.

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
