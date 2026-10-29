# Internal community operations API
Server only: /internal/admin/**. Shared header X-Community-Admin-Key required (COMMUNITY_ADMIN_KEY must have at least 32 characters; default empty disables all access), actor X-Community-Admin-Actor is URL-encoded UTF-8, decoded and validated max64, audited. No browser calls. Envelope `{code,message,data}`. Management proxy `/community/**` retains path remainder and forwards envelope. Inputs JSON; updates PUT; deletion DELETE. Page query `pageNo=1,size=20,query=''`, size1..50. Page `{content,totalElements,totalPages,number,size}` (number zero based). All IDs numeric.

- GET /users => page `{id,username,alias,email,avatar,status,active,createTime}`; optional query searches username/alias/email.
- GET /users/{id} => same +bio; never password/token/auth_version.
- PUT /users/{id}/status `{status:boolean,reason:string(1..255)}` => user; disable/restore increments auth_version and audits.
- GET /posts => page `{id,title,username,status,top,essence,comments,collects,likes,createTime,modifyTime}`; query title/author; optional `status=PUBLISHED|PENDING|HIDDEN`.
- GET /posts/{id} => same +content,version,tags,moderationReason. tags is [{id,name,topicCount}], not strings.
- PUT /posts/{id}/moderation `{version:integer>=0,status:PUBLISHED|PENDING|HIDDEN,top:boolean,essence:boolean,reason:string(1..255)}` => detail. Version must match the detail the reviewer inspected; stale reviews return 409 without changing status, flags, notifications or audit records. Reload and inspect the latest content before retrying. Both flags required; no separate flag route. A successful review bumps version, notifies the author and audits the reviewed version.
- DELETE /posts/{id}?reason=... => null; require reason1..255, cascade comments/likes/bookmarks/notifications and tags counters; audits.
- GET /comments => page `{id,postId,postTitle,parentId,username,content,visible,createdAt}`; query content/username; optional postId.
- GET /comments/{id} => comment object.
- PUT /comments/{id}/visibility `{visible:boolean,reason:string(1..255)}` => comment; hide root hides descendants publicly; counters recomputed; audits.
- DELETE /comments/{id}?reason=... => null; cascades replies, recomputes counters, audits.
- GET /billboards => page `{id,content,visible,createTime}`; GET /billboards/{id}; POST /billboards `{content:string1..255,visible:boolean}`; PUT /billboards/{id} same; DELETE /billboards/{id}.
- GET /tips => page `{id,content,author,enabled}`; GET /tips/{id}; POST /tips `{content:string1..255,author:string<=255,enabled:boolean}`; PUT /tips/{id} same; DELETE /tips/{id}.
- GET /promotions => page `{id,title,link,description}`; GET /promotions/{id}; POST /promotions `{title:string1..255,link:http(s) URL<=255,description:string<=255}`; PUT /promotions/{id} same; DELETE /promotions/{id}.
- GET /tags => page `{id,name,topicCount}`; GET /tags/{id}; POST /tags `{name:string1..32}`; PUT /tags/{id} same (normalizes lowercase, unique); DELETE /tags/{id} returns409 if used.
- GET /audit => page `{id,actor,action,resource,resourceId,detail,createdAt}`; query actor/action/resource. Immutable; detail contains action reason or safe changed-field summary, never credentials.

Every mutation audits action and actor. Private member conversations/notifications never returned through moderation endpoints. No database reset endpoint.

New posts and all author edits (including edits of published posts) enter PENDING. Only a version-checked operator review can make them PUBLISHED. Existing published posts retain their status when migration V6 changes the default for future inserts.
