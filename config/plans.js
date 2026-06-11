/**
 * Canonical HoldOff pricing plan definitions.
 * Used by routes/checkout.js, routes/stripe-webhook.js, views/upgrade.ejs,
 * and any other code that needs tier metadata.
 */

/**
 * Stripe checkout payment links per tier.
 * CashApp Pay is enabled on the Stripe account — Stripe Checkout surfaces it automatically.
 */
const TIER_URLS = {
  online_weekly:  'https://buy.stripe.com/6oU5kEeZ35TP2rSbqn2sM0c',
  app_weekly:     'https://buy.stripe.com/bJe3cw7wB3LH2rS2TR2sM0d',
  online_monthly: 'https://buy.stripe.com/28EbJ23gl2HDgiIeCz2sM07',
  app_monthly:    'https://buy.stripe.com/5kQcN6cQVfupd6weCz2sM08',
  online_annual:  'https://buy.stripe.com/6oUeVeaIN5TPaYo0LJ2sM09',
  app_annual:     'https://buy.stripe.com/6oU9AU5otfup0jK2TR2sM0a',
  lifetime:       'https://buy.stripe.com/4gMfZi6sx4PLc2s9if2sM0b',
};

/**
 * All HoldOff pricing tiers.
 * @property {string}  id              — unique tier key (matches TIER_URLS keys)
 * @property {string}  label           — display name
 * @property {string}  interval        — 'weekly' | 'monthly' | 'yearly' | 'once'
 * @property {number}  price           — price in USD
 * @property {string}  priceDisplay   — human-readable price string
 * @property {string}  membershipType  — 'online' | 'app' | 'lifetime'
 * @property {boolean} highlight      — whether to show "Most popular" badge in UI
 */
const PLANS = [
  {
    id: 'online_weekly',
    label: 'HoldOff Online',
    interval: 'weekly',
    price: 4.99,
    priceDisplay: '$4.99/wk',
    membershipType: 'online',
    highlight: false,
  },
  {
    id: 'online_monthly',
    label: 'HoldOff Online',
    interval: 'monthly',
    price: 9.99,
    priceDisplay: '$9.99/mo',
    membershipType: 'online',
    highlight: false,
  },
  {
    id: 'online_annual',
    label: 'HoldOff Online',
    interval: 'yearly',
    price: 99,
    priceDisplay: '$99/yr',
    membershipType: 'online',
    highlight: false,
  },
  {
    id: 'app_weekly',
    label: 'HoldOff App',
    interval: 'weekly',
    price: 7.49,
    priceDisplay: '$7.49/wk',
    membershipType: 'app',
    highlight: false,
  },
  {
    id: 'app_monthly',
    label: 'HoldOff App',
    interval: 'monthly',
    price: 14.99,
    priceDisplay: '$14.99/mo',
    membershipType: 'app',
    highlight: true,
  },
  {
    id: 'app_annual',
    label: 'HoldOff App',
    interval: 'yearly',
    price: 149,
    priceDisplay: '$149/yr',
    membershipType: 'app',
    highlight: false,
  },
  {
    id: 'lifetime',
    label: 'HoldOff Lifetime',
    interval: 'once',
    price: 299,
    priceDisplay: '$299 once',
    membershipType: 'lifetime',
    highlight: false,
  },
];

module.exports = { PLANS, TIER_URLS };
