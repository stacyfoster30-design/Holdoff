/**
 * Shared AI verdict-calling logic for HoldOff.
 *
 * AI call chain: Anthropic Claude (primary, 10s) → OpenAI GPT-4o (fallback, 5s) → static HOLD
 *
 * Polsia proxy has been removed. We now call Anthropic and OpenAI directly.
 * Required env vars:
 *   ANTHROPIC_API_KEY  — starts with sk-ant-
 *   OPENAI_API_KEY     — direct OpenAI key (not a proxy)
 */

const OpenAI  = require('openai');
const Anthropic = require('@anthropic-ai/sdk');
const crypto  = require('crypto');
const { logVerdictCall } = require('../db/healthchecks');

// ── Timeouts ──────────────────────────────────────────────────────────────────
const ANTHROPIC_TIMEOUT_MS    = 10000;  // Claude primary — 10s
const OPENAI_TIMEOUT_MS       = 5000;   // GPT-4o fallback — 5s
const HANDLER_HARD_TIMEOUT_MS = 14000;  // Hard ceiling — never hang the handler

// ── Clients ───────────────────────────────────────────────────────────────────
const anthropic = process.env.ANTHROPIC_API_KEY
  ? new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY, timeout: ANTHROPIC_TIMEOUT_MS, maxRetries: 0 })
  : null;

const openaiClient = process.env.OPENAI_API_KEY
  ? new OpenAI({ apiKey: process.env.OPENAI_API_KEY, timeout: OPENAI_TIMEOUT_MS, maxRetries: 0 })
  : null;

// ── Smart rule-based fallback (runs without API keys) ────────────────────────
// Produces emotionally resonant verdicts based on message content analysis.
// Used when both Anthropic and OpenAI are unavailable.

function analyzeLocally(message) {
  const m = (message || '').toLowerCase();
  const words = m.split(/\s+/);
  const len = words.length;

  // Pattern detection
  const isLateNight   = new Date().getHours() >= 22 || new Date().getHours() <= 4;
  const hasQuestion   = m.includes('?') || /why|what|did you|do you|are you|have you/.test(m);
  const hasUrgency    = /now|tonight|today|please|need to|have to|asap|right now|still|yet|hours|days|know you|i know|awake|seen|read it/.test(m);
  const hasMiss       = /miss you|thinking about you|can't stop|keep thinking|wondering/.test(m);
  const hasAngry      = /wtf|fuck|why haven't|why didn't|why won't|never|always|you never|you always/.test(m);
  const hasDoubt      = /did i do|was i|did you even|do you even|do you still|am i|are we/.test(m);
  const hasVulnerable = /love you|love u|need you|scared|hurt|feel like|i'm sorry|sorry if/.test(m);
  const hasPanic      = /please respond|answer me|hello|still there|read this|saw this/.test(m);
  const isShort       = len <= 8;
  const isLong        = len >= 40;

  // Score emotional urgency (higher = more likely stress response)
  let urgencyScore = 0;
  if (isLateNight) urgencyScore += 3;
  if (hasUrgency)  urgencyScore += 2;
  if (hasPanic)    urgencyScore += 3;
  if (hasAngry)    urgencyScore += 2;
  if (hasDoubt)    urgencyScore += 1;
  if (isLong)      urgencyScore += 1;

  // Determine verdict
  // Pure love/care — clean send
  const isPureLove = /^(i love you|i love u|love you|i care about you|you matter to me)[.!\s]*$/i.test(message.trim());
  if (isPureLove) {
    return {
      verdict: 'SEND',
      pattern: 'Clean expression',
      whats_happening: "This is clear, clean, and comes from a real place. No urgency, no pressure — just honest feeling.",
      grounded_voice: "Send it. Three words from a grounded place are worth more than a paragraph from an anxious one.",
      rewrite: null,
      confidence: 0.92,
      verdict_source: 'local-analysis',
    };
  }

  if (urgencyScore >= 3 || hasAngry || hasPanic) {
    return {
      verdict: 'HOLD',
      pattern: isAngry(m) ? 'Reactive escalation' : isLateNight ? 'Late-night flooding' : 'Anxious urgency spiral',
      whats_happening: isLateNight
        ? "It's late and your nervous system is activated. This message is being written from a place of fear, not clarity. The version of you who sends this at 2am is not the version who will feel good about it tomorrow."
        : hasAngry
        ? "This message has accusatory energy — words like 'never' and 'always' signal that you're in fight mode, not connection mode. Sending this now will escalate, not repair."
        : "You're seeking reassurance through urgency. The more you push for a response right now, the more you signal anxiety — and anxiety pushes people away.",
      grounded_voice: isLateNight
        ? "Sleep on it. If it still matters at 9am, send it then — calmly, clearly, without the 2am weight attached."
        : hasAngry
        ? "Write what you actually want to say underneath the anger. Start with 'I feel' instead of 'you never.' That's the message worth sending."
        : "You deserve a real response, not a panic response. Give it 24 hours. Your worth isn't determined by how fast they reply.",
      rewrite: hasAngry
        ? "I've been feeling disconnected from you lately. Can we talk when you have space?"
        : hasMiss
        ? "Hey — been thinking about you. Hope you're doing okay."
        : null,
      confidence: 0.82,
      verdict_source: 'local-analysis',
    };
  }

  if (hasVulnerable && !hasUrgency && !hasDoubt) {
    return {
      verdict: 'SEND',
      pattern: 'Genuine vulnerability',
      whats_happening: "This message comes from a real place. You're being honest about what you feel without demanding anything in return. That's courage, not weakness.",
      grounded_voice: "Send it. Vulnerability expressed cleanly — without ultimatums or urgency — is the foundation of real intimacy. You're not performing here. You mean this.",
      rewrite: null,
      confidence: 0.78,
      verdict_source: 'local-analysis',
    };
  }

  if (hasMiss && !hasUrgency && !isLateNight) {
    return {
      verdict: 'REWRITE',
      pattern: 'Reaching out',
      whats_happening: "There's genuine feeling here, but the framing might land heavier than you intend. 'I miss you' said plainly is powerful. Said with weight or pressure, it can feel like a burden.",
      grounded_voice: "If you want to reconnect, the cleanest path is a low-stakes opener — something easy to respond to. Lead with curiosity, not need.",
      rewrite: "Hey. Was thinking about you today — hope you're good.",
      confidence: 0.71,
      verdict_source: 'local-analysis',
    };
  }

  // Default: thoughtful HOLD
  return {
    verdict: 'HOLD',
    pattern: isLateNight ? 'Late-night impulse' : 'Unprocessed emotion',
    whats_happening: isLateNight
      ? "You're awake and they're on your mind. That's about you right now, not about the relationship. The message can wait until morning when you can see it clearly."
      : "Before you send this, ask: what do you actually want back? If the honest answer is reassurance — this message probably won't give it to you.",
    grounded_voice: "Hold for now. Not because your feelings aren't valid — they are. But because this moment deserves more than a reactive message. Come back to it tomorrow.",
    rewrite: null,
    confidence: 0.68,
    verdict_source: 'local-analysis',
  };
}

function isAngry(m) {
  return /fuck|wtf|why (haven't|didn't|won't)|you never|you always|i can't believe/.test(m);
}

const STATIC_HOLD = analyzeLocally;

// ── Attachment style map ──────────────────────────────────────────────────────
const STYLE_MAP = {
  'ANX': { label: 'Anxious-Preoccupied', scores: ['anxious_score', 'anxious'] },
  'AVO': { label: 'Avoidant-Dismissive', scores: ['avoidant_score', 'avoidant'] },
  'FA':  { label: 'Fearful-Avoidant',    scores: ['fearful_score', 'fearful'] },
  'SEC': { label: 'Secure',              scores: ['secure_score', 'secure'] },
  'anxious_preoccupied': 'ANX',
  'dismissive_avoidant': 'AVO',
  'fearful_avoidant':    'FA',
  'secure':              'SEC',
};

function resolveStyleCode(raw) {
  if (!raw) return null;
  const s = String(raw).trim().toUpperCase();
  if (STYLE_MAP[s]) return s;
  return STYLE_MAP[String(raw).toLowerCase()] || null;
}

function buildStyleInstructions(profile) {
  if (!profile) return '';
  const dominant  = resolveStyleCode(profile.dominant_style);
  const secondary = resolveStyleCode(profile.secondary_style);
  if (!dominant) return '';

  const scores = {
    ANX: profile.anxious_score  || 0,
    AVO: profile.avoidant_score || 0,
    FA:  profile.fearful_score  || 0,
    SEC: profile.secure_score   || 0,
  };

  const hasStyle = (code) => dominant === code || scores[code] >= 4;
  const lines = [];

  if (dominant === 'ANX' || hasStyle('ANX')) {
    lines.push('YOUR ATTACHMENT CONTEXT — ANXIOUS-PREOCCUPIED STYLE DETECTED:');
    lines.push('- FLAG over-explaining language: long justifications, disclaimers, "sorry but" prefixes');
    lines.push('- FLAG validation-seeking phrasing: "are you mad", "is everything okay", "did I do something wrong"');
    lines.push('- FLAG premature compliance: \"okay fine I won\'t bring it up again\"');
    lines.push('- FLAG apology spirals: \"I\'m sorry I\'m so sorry I shouldn\'t have said that\"');
    lines.push('RESPONSE FRAME: "You are over-explaining to relieve your own anxiety. Cut the last 2–3 sentences. State the core point without the explanation."');
  }

  if (dominant === 'AVO' || hasStyle('AVO')) {
    lines.push('YOUR ATTACHMENT CONTEXT — AVOIDANT-DISMISSIVE STYLE DETECTED:');
    lines.push('- FLAG emotional stonewalling: going silent mid-conversation');
    lines.push('- FLAG dismissive one-word responses: "k", "fine", "whatever", "👍"');
    lines.push('- FLAG defensive detachment: deflection, "lol", "idk", "cool story"');
    lines.push('- FLAG pull-back after closeness: switching topics, going cold');
    lines.push('RESPONSE FRAME: "This reads as cold. If you value this connection, add one sentence of explicit clarity. Do not just shut down — say what you actually mean."');
  }

  if (dominant === 'FA' || hasStyle('FA')) {
    lines.push('YOUR ATTACHMENT CONTEXT — FEARFUL-AVOIDANT STYLE DETECTED:');
    lines.push('- FLAG hot-cold whiplash: intense paragraph then radio silence');
    lines.push('- FLAG hyper-reactive escalation: messages that contradict each other mid-thread');
    lines.push('- FLAG immediate undo attempts: "nevermind forget I said anything"');
    lines.push('- FLAG self-sabotage: \"ignore this I shouldn\'t have sent it\"');
    lines.push('RESPONSE FRAME: "You are cycling hard right now. Use the Spiral Lock — set a 30-minute timer before you send anything. Your past self is asking you to wait."');
  }

  if (dominant === 'SEC') {
    lines.push('YOUR ATTACHMENT CONTEXT — SECURE STYLE:');
    lines.push('AFFIRM mode: this user generally communicates well. If the message is clean, say so directly.');
    lines.push('Still flag genuinely risky content — frame as "you know better than this."');
    lines.push('RESPONSE FRAME: "This is a reasonable message. Still — read it one more time. Is there anything you would say differently in person?"');
  }

  if (secondary && dominant !== secondary) {
    const scoreDiff = Math.abs((scores[dominant] || 0) - (scores[secondary] || 0));
    if (scoreDiff < 2) {
      lines.push(`STYLE BLEND DETECTED (${dominant}+${secondary}, gap=${scoreDiff}):`);
      if ((dominant === 'SEC' && secondary === 'ANX') || (dominant === 'ANX' && secondary === 'SEC')) {
        lines.push('SEC baseline with anxiety under the surface — affirm the good instinct, then flag specific ANX patterns.');
      }
    }
  }

  if (dominant === 'FA' || secondary === 'FA') {
    if (dominant !== 'FA') {
      lines.push('HIGH VOLATILITY OVERRIDE: This user has Fearful-Avoidant traits alongside other attachment patterns.');
    }
    lines.push('SPIRAL LOCK REQUIRED: Regardless of verdict, always include this recommendation: "Use the Spiral Lock — set a 30-minute timer before you send anything."');
  }

  return lines.length ? '\n\n' + lines.join('\n') + '\n' : '';
}

// ── System Prompt ─────────────────────────────────────────────────────────────
const SYSTEM_PROMPT = `You are HoldOff — a text message analysis tool for people who text things they later wish they hadn't. You detect patterns across ALL insecure attachment styles, not just anxious attachment. Before every response, read the user's message completely. Respond ONLY to what they actually wrote. Never return a templated response.

ATTACHMENT PATTERN COVERAGE — name the style explicitly in the pattern field:

ANXIOUS ATTACHMENT patterns:
- Double-texting / status checks: "are you mad at me", "did I do something wrong", "hello??"
- Over-apologizing / excessive validation-seeking: long apologies with self-flagellation
- Response-time anxiety: "why hasn't he replied", "it's been 3 hours"
- Reassurance-seeking: "do you still love me", "is everything okay", "are we okay"
- Escalating intensity mid-conversation: adding more pressure with each message

AVOIDANT ATTACHMENT patterns:
- One-word replies / minimal engagement: "k", "👍", "whatever"
- Stonewalling mid-conversation: going silent in the middle of a thread
- Hours-to-days delayed responses framed as dismissiveness
- Deflecting emotional content: "lol", "idk", "whatever", "cool story"
- Pulling away after emotional closeness: switching topics, going cold

FEARFUL-AVOIDANT ATTACHMENT patterns:
- Hot-cold: intense paragraph followed by radio silence
- Push-pull: reaching out then retreating mid-conversation
- Mixed signals / contradictory messages: "i want to see you" then "actually nvm"
- Self-sabotage confessions mid-text: "ignore this I shouldn't have sent it"

DISMISSIVE-AVOIDANT ATTACHMENT patterns:
- Clipped, robotic responses: "ok", "sure", "fine"
- Sarcasm/deflection masking real feelings: "oh wow", "how original"
- Shutting down partner's emotional expressions
- Breadcrumbing: "yeah", "ok", "👍" to string someone along

CORE RULES:
- Quote at least one specific phrase from the user's actual message in "whats_happening" or "grounded_voice"
- Name the pattern in attachment-style-specific terms — not generic labels
- Vary response length and tone based on message severity and type
- SEND verdicts MUST happen when messages are genuinely clean — not as a safe default
- HOLD verdicts MUST have specific reasoning tied to the actual message content
- REWRITE verdicts MUST include a concrete alternative, not just "rewrite it"

VERDICT MUST be one of: SEND | HOLD | REWRITE

Respond ONLY with valid JSON. No markdown. No preamble.

{
  "verdict": "SEND" | "HOLD" | "REWRITE",
  "pattern": "<attachment pattern name>",
  "whats_happening": "<1–2 sentences — what's actually going on in this message>",
  "grounded_voice": "<direct, warm coaching voice — 1–3 sentences>",
  "rewrite": "<rewritten message if verdict is REWRITE, otherwise null>",
  "confidence": <0.0–1.0>,
  "attachment_style": "ANX" | "AVO" | "FA" | "SEC" | null
}`;

// ── Cookie helpers ────────────────────────────────────────────────────────────
function parseCookies(cookieHeader = '') {
  return Object.fromEntries(
    (cookieHeader || '').split(';').map(c => c.trim().split('=').map(decodeURIComponent))
  );
}

function getVerdictCount(cookies) {
  return parseInt(cookies?.hf_vc || '0', 10);
}

function extractProInfo(cookies) {
  try {
    const raw = cookies?.hf_pro;
    if (!raw) return null;
    return JSON.parse(Buffer.from(raw, 'base64').toString('utf8'));
  } catch {
    return null;
  }
}

// ── Anthropic call ────────────────────────────────────────────────────────────
async function callAnthropic(systemPrompt, userContent, log) {
  if (!anthropic) throw new Error('Anthropic client not initialised — missing ANTHROPIC_API_KEY');

  log('anthropic_start');
  const response = await anthropic.messages.create({
    model:      'claude-opus-4-5',
    max_tokens: 1024,
    system:     systemPrompt,
    messages:   [{ role: 'user', content: userContent }],
  });
  log('anthropic_done');

  const raw = response.content?.[0]?.text || null;
  return { raw, source: 'anthropic' };
}

// ── OpenAI direct call ────────────────────────────────────────────────────────
async function callOpenAI(systemPrompt, userContent, log) {
  if (!openaiClient) throw new Error('OpenAI client not initialised — missing OPENAI_API_KEY');

  log('openai_start');
  const response = await openaiClient.chat.completions.create({
    model:       'gpt-4o',
    temperature: 0.4,
    messages: [
      { role: 'system',  content: systemPrompt },
      { role: 'user',    content: userContent },
    ],
  });
  log('openai_done');

  const raw = response.choices?.[0]?.message?.content || null;
  return { raw, source: 'openai' };
}

// ── Main fallback chain ───────────────────────────────────────────────────────
/**
 * callWithFallback — try Anthropic first, fall back to OpenAI direct.
 * Returns { raw: string, source: 'anthropic' | 'openai' | 'fallback' }
 */
async function callWithFallback(systemPrompt, userContent, log, attachmentProfile) {
  const styleBlock = buildStyleInstructions(attachmentProfile);
  const fullSystemPrompt = styleBlock
    ? systemPrompt.replace('\n\nVERDICT MUST', styleBlock + '\n\nVERDICT MUST')
    : systemPrompt;

  // 1. Anthropic Claude (primary)
  try {
    const result = await callAnthropic(fullSystemPrompt, userContent, log);
    if (result.raw) return result;
  } catch (err) {
    log('anthropic_failed', `err=${err.message}`);
  }

  // 2. OpenAI GPT-4o (fallback)
  try {
    const result = await callOpenAI(fullSystemPrompt, userContent, log);
    if (result.raw) return result;
  } catch (err) {
    log('openai_failed', `err=${err.message}`);
  }

  // 3. Static HOLD — all AI paths exhausted
  log('all_ai_paths_failed');
  return { raw: null, source: 'fallback' };
}

module.exports = {
  callWithFallback,
  buildStyleInstructions,
  parseCookies,
  extractProInfo,
  getVerdictCount,
  HANDLER_HARD_TIMEOUT_MS,
  STATIC_HOLD,
  SYSTEM_PROMPT,
};
