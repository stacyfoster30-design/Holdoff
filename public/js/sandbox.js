/**
 * HoldOff Sandbox — client-side intercept trial for unauthenticated landing page visitors.
 * Zero backend calls. Zero auth required. Fully self-contained.
 */
(function () {
  'use strict';

  const SESSION_KEY = 'holdoff_sandbox_trial';

  // ─── Urgency flag detectors ────────────────────────────────────────────────

  function wordCount(text) {
    return text.trim().split(/\n|\r|\n\r|\r\n/).join(' ').split(/\b/).filter(Boolean).length;
  }

  function flagWordCount(text) {
    return wordCount(text) > 20;
  }

  function flagUppercase(text) {
    const letters = text.replace(/[^a-zA-Z]/g, '');
    if (letters.length < 4) return false;
    const upper = letters.replace(/[A-Z]/g, '').length;
    return (letters.length - upper) / letters.length > 0.30;
  }

  function flagRepeatedQuestions(text) {
    return (text.match(/\u003F/g) || []).length >= 2;
  }

  function flagApology(text) {
    const lower = text.toLowerCase();
    return lower.includes("i'm sorry") || lower.includes("im sorry") || lower.includes("sorry");
  }

  function flagSpiral(text) {
    const lower = text.toLowerCase();
    return lower.includes("i can't stop") || lower.includes("i cant stop") || lower.includes("i miss you");
  }

  function flagAnxiety(text) {
    const lower = text.toLowerCase();
    return lower.includes("are you mad") || lower.includes("do you hate") || lower.includes("did i do something wrong") || lower.includes("what did i do");
  }

  function getFlags(text) {
    return {
      wordCount: flagWordCount(text),
      uppercase: flagUppercase(text),
      repeatedQuestions: flagRepeatedQuestions(text),
      apology: flagApology(text),
      spiral: flagSpiral(text),
      anxiety: flagAnxiety(text),
    };
  }

  function countFlags(flags) {
    return Object.values(flags).filter(Boolean).length;
  }

  // ─── DOM elements ───────────────────────────────────────────────────────────

  const container   = document.getElementById('sandbox-container');
  const textarea    = document.getElementById('sandbox-textarea');
  const testBtn     = document.getElementById('sandbox-test-btn');
  const verdictZone = document.getElementById('sandbox-verdict');
  const modal       = document.getElementById('sandbox-modal');
  const modalBody   = document.getElementById('sandbox-modal-body');
  const modalCta    = document.getElementById('sandbox-modal-cta');
  const paywallCta  = document.getElementById('sandbox-paywall');

  if (!container || !textarea || !testBtn || !verdictZone || !modal || !modalBody || !modalCta || !paywallCta) {
    return; // not on this page
  }

  // ─── Countdown ─────────────────────────────────────────────────────────────

  function runCountdown(callback) {
    let remaining = 5;
    modalBody.dataset.countdown = '5';
    modalBody.classList.add('sandbox-modal__body--counting');

    const tick = setInterval(function () {
      remaining--;
      modalBody.dataset.countdown = String(remaining);
      if (remaining <= 0) {
        clearInterval(tick);
        modalBody.classList.remove('sandbox-modal__body--counting');
        callback();
      }
    }, 1000);
  }

  // ─── Intercept trigger ─────────────────────────────────────────────────────

  function triggerIntercept(text) {
    textarea.disabled = true;
    testBtn.disabled = true;

    // Stamp session
    try { sessionStorage.setItem(SESSION_KEY, JSON.stringify({ ts: Date.now(), intercepted: true })); } catch (e) {}

    modal.classList.add('is-open');

    runCountdown(function () {
      // Show behavioral profile
      modalBody.innerHTML = `
        <div class="sandbox-modal__profile">
          <div class="sandbox-modal__badge">Anxious-Preoccupied pattern detected</div>
          <p class="sandbox-modal__profile-text">
            Your texting spikes at night.<br>
            You overexplain. You send when you should wait.
          </p>
        </div>
        <p class="sandbox-modal__final">Send it? Or wait until morning?</p>
      `;
      modalCta.textContent = 'Wait until morning';
      modalCta.style.display = 'inline-block';

      // Show paywall
      paywallCta.style.display = 'block';
    });
  }

  // ─── Input handler ─────────────────────────────────────────────────────────

  testBtn.addEventListener('click', function () {
    const text = textarea.value.trim();
    if (!text) return;

    const flags = getFlags(text);
    const flagCount = countFlags(flags);

    if (flagCount >= 2) {
      triggerIntercept(text);
    } else {
      // Safe message — green verdict
      textarea.disabled = true;
      testBtn.disabled = true;
      verdictZone.style.display = 'block';
      verdictZone.innerHTML = `
        <div class="sandbox-verdict sandbox-verdict--safe">
          <div class="sandbox-verdict__icon">✓</div>
          <p class="sandbox-verdict__label">Low urgency — you're good to send.</p>
          <p class="sandbox-verdict__note">Want to see what high-urgency looks like? Try: "Hey, are you mad at me? I'm sorry, I can't stop thinking about this. Please tell me something. What did I do??"</p>
        </div>
      `;
      try { sessionStorage.setItem(SESSION_KEY, JSON.stringify({ ts: Date.now(), intercepted: false })); } catch (e) {}
    }
  });

  // Allow Ctrl/Cmd + Enter to submit
  textarea.addEventListener('keydown', function (e) {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault();
      testBtn.click();
    }
  });

})();