# HoldOff PWA — Todo

## Foundation
- [x] Database schema: users, verdicts, interpretations, journal_entries, community_posts, post_reactions, quiz_results, contacts, contact_insights, spiral_locks, chronicle_tips
- [x] Global CSS: dark purple theme, Fraunces + DM Sans fonts, design tokens, utility classes
- [x] PWA manifest and mobile viewport setup
- [x] App shell with bottom tab navigation (Filter, Interpret, Companions, Journal, Community)
- [x] Routing setup in App.tsx with all routes

## Landing Page
- [x] Hero section with HoldOff branding and CTA
- [x] How it works section (3-step pause flow)
- [x] Pricing/plans section
- [x] Footer with links

## Auth & Onboarding
- [x] Auth flow (login/signup via Manus OAuth)
- [x] Attachment style onboarding quiz (5 questions)
- [x] Quiz result storage per user

## Filter / Verdict Screen
- [x] Message input textarea with character count (3000 chars)
- [x] Context field (optional)
- [x] Attachment style context selector
- [x] AI verdict engine: SEND / WAIT / DO NOT SEND
- [x] Verdict history stored in DB per user
- [x] Free verdict limit mention (3 free)

## Interpreter Screen
- [x] Received message input
- [x] AI emotional decoding: what it really means
- [x] Attachment style signals detected
- [x] Suggested response generation
- [x] Interpretation history saved to DB

## AI Companion Chat
- [x] Sadie ✨ persona (secure-leaning guide) with system prompt
- [x] Stacy persona (fearful-avoidant) with system prompt
- [x] Danny persona (avoidant-dismissive) with system prompt
- [x] Companion selector page with cards
- [x] Chat responses via tRPC
- [x] Conversation history per session (last 10 messages)

## Contacts & Relationship Analysis
- [x] Contacts list screen
- [x] Add contact form (name, relationship type, duration)
- [x] Contact detail page
- [x] Red/yellow/green flags display
- [x] Compatibility score and attachment style fit
- [x] Contact insights AI generation

## Journal
- [x] Private journal entry creation
- [x] Mood tagging (Calm, Anxious, Spiraling, Victory)
- [x] Entry list view per authenticated user
- [x] Entry delete

## Community Feed
- [x] Anonymous post creation with mood tags
- [x] Feed display with mood filter
- [x] Emoji reactions on posts
- [x] Anonymous display names (adjective + noun)

## Chronicle / Insights
- [x] Personalized tips based on usage patterns
- [x] Verdict stats breakdown (SEND/WAIT/DO NOT SEND counts)
- [x] Recent verdict history display

## Attachment Style Quiz
- [x] 5-question quiz with attachment style scoring
- [x] Result display (Secure/Anxious/Avoidant/Fearful-Avoidant)
- [x] Result storage per user
- [x] Score breakdown visualization

## Pricing / Paywall
- [x] Pricing page with tiers (Free/Weekly/Monthly/Annual/Lifetime)

## Profile & Settings
- [x] Attachment style result display
- [x] Verdict stats (total, breakdown by verdict type)
- [x] Account info display
- [x] Quick links to all features

## Server Procedures (tRPC)
- [x] filter.analyze — AI verdict
- [x] filter.history — verdict history per user
- [x] filter.stats — verdict stats
- [x] interpret.analyze — AI interpretation
- [x] companion.chat — companion chat
- [x] journal.create / list / delete
- [x] community.feed / post / react
- [x] quiz.save / get
- [x] contacts.list / create / get / update / delete / analyze
- [x] chronicle.tips

## Founder Story
- [x] FounderStoryPage with brand narrative

## Polish
- [x] Mobile-first responsive layout
- [x] Loading states and spinners
- [x] Error handling and empty states
- [x] Animations (fade-up, stagger-children)
- [ ] Vitest tests for core procedures
- [ ] Spiral Lock feature (lock after too many DO NOT SEND)
- [ ] Mental health disclaimer in companion chat

## Animated Avatar System
- [x] AnimatedAvatar component with float/blink CSS animations and expression-swap
- [x] detectSentiment() utility mapping AI response text to neutral/happy/thinking
- [x] Expression swapping in CompanionChatPage driven by AI response sentiment
- [x] Thinking expression shown while AI is loading/typing
- [x] Mini avatar in chat header + full-body avatar panel during chat
- [x] Companion cards on CompanionsPage use animated avatars
- [x] Landing page companion showcase with hover-happy expression effect
- [x] Stacy, Danny, Dan, Sadie base avatars uploaded from user-provided cartoon art
- [x] 12 expression variant images queued (3 per character × 4 characters)

## Landing Page Redesign (Marketing Site)
- [x] Hero with tagline, CTA, and sign-in flow
- [x] Embedded live Filter demo (works without login, 3 free verdicts)
- [x] How it works — 3-step section
- [x] AI Companions showcase with animated avatars
- [x] Features grid (6 features)
- [x] Founder quote + story link
- [x] Pricing teaser (Free vs Pro)
- [x] Final CTA + footer
