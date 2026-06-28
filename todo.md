# HoldOff App TODO

## Fixes & Improvements

- [x] Fix web app project structure (manus-web files moved to root)
- [x] Fix LLM response parsing bug - routers.ts was using `choices[0].message.content` (OpenAI format) correctly
- [x] Upload Stacy's real photo and update AnimatedAvatar component
- [x] Add `dan` companion persona to server router (companion.chat)
- [x] Add `dan` companion to CompanionsPage listing
- [x] Add `dan` companion to CompanionChatPage with metadata
- [x] Add `expression` return field to companion.chat procedure
- [x] Write Vitest tests for core tRPC procedures (filter, interpret, companion, journal, spiral, auth)
- [x] Create vitest.config.ts pointing to server test files
- [x] All 15 tests passing (2 test files)
- [x] TypeScript: 0 errors

## Pending Features

- [ ] Push latest fixes to GitHub
- [ ] Google Play Store APK build via Capacitor
