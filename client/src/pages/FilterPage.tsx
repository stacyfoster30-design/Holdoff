import { useState } from "react";
import { trpc } from "@/lib/trpc";
import { useAuth } from "@/_core/hooks/useAuth";
import { getLoginUrl } from "@/const";
import { toast } from "sonner";
import { Link } from "wouter";

const ATTACHMENT_STYLES = [
  { value: "secure", label: "Secure" },
  { value: "anxious", label: "Anxious" },
  { value: "avoidant", label: "Avoidant" },
  { value: "fearful", label: "Fearful-Avoidant" },
];

type VerdictResult = {
  verdict: "SEND" | "WAIT" | "DO NOT SEND";
  explanation: string;
  patternName: string;
  reframe: string | null;
  rewrite: string | null;
};

const VERDICT_CONFIG = {
  "SEND": {
    color: "verdict-send",
    icon: "✓",
    bg: "bg-emerald-500/10 border-emerald-500/30",
    text: "text-emerald-400",
    label: "SEND",
  },
  "WAIT": {
    color: "verdict-wait",
    icon: "⏸",
    bg: "bg-amber-500/10 border-amber-500/30",
    text: "text-amber-400",
    label: "WAIT",
  },
  "DO NOT SEND": {
    color: "verdict-nosend",
    icon: "✕",
    bg: "bg-rose-500/10 border-rose-500/30",
    text: "text-rose-400",
    label: "DO NOT SEND",
  },
};

export default function FilterPage() {
  const { user, isAuthenticated } = useAuth();
  const [message, setMessage] = useState("");
  const [context, setContext] = useState("");
  const [attachmentStyle, setAttachmentStyle] = useState("");
  const [result, setResult] = useState<VerdictResult | null>(null);
  const [loading, setLoading] = useState(false);

  const analyzeMutation = trpc.filter.analyze.useMutation({
    onSuccess: (data) => {
      setResult(data as VerdictResult);
      setLoading(false);
    },
    onError: (err) => {
      toast.error("Something went wrong. Try again.");
      setLoading(false);
    },
  });

  const handleAnalyze = () => {
    if (!message.trim()) {
      toast.error("Paste your message first.");
      return;
    }
    setLoading(true);
    setResult(null);
    analyzeMutation.mutate({
      message: message.trim(),
      context: context.trim() || undefined,
      attachmentStyle: attachmentStyle || undefined,
    });
  };

  const handleReset = () => {
    setResult(null);
    setMessage("");
    setContext("");
  };

  const verdictConfig = result ? VERDICT_CONFIG[result.verdict] : null;

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="font-display text-2xl font-bold">Filter</h1>
            <p className="text-muted-foreground text-sm mt-0.5">Should you hold off?</p>
          </div>
          <div className="flex items-center gap-2">
            <Link href="/contacts" className="icon-btn">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
            </Link>
            <Link href="/chronicle" className="icon-btn">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
              </svg>
            </Link>
          </div>
        </div>
      </div>

      <div className="page-body">
        {!result ? (
          <div className="space-y-4">
            {/* Message input */}
            <div>
              <label className="input-label">What are you about to send?</label>
              <p className="text-xs text-muted-foreground mb-2">Paste it. Be honest. No one's watching.</p>
              <textarea
                className="holdoff-textarea"
                placeholder="Type or paste the message here..."
                value={message}
                onChange={(e) => setMessage(e.target.value)}
                rows={5}
                maxLength={3000}
              />
              <div className="text-right text-xs text-muted-foreground mt-1">{message.length} / 3000</div>
            </div>

            {/* Context */}
            <div>
              <label className="input-label">What's going on? <span className="text-muted-foreground font-normal">(optional but helps)</span></label>
              <textarea
                className="holdoff-textarea"
                placeholder="Their last message, how long they've been quiet, what you're feeling..."
                value={context}
                onChange={(e) => setContext(e.target.value)}
                rows={3}
                maxLength={500}
              />
            </div>

            {/* Attachment style */}
            <div>
              <label className="input-label">Your attachment style <span className="text-muted-foreground font-normal">(optional)</span></label>
              <div className="grid grid-cols-2 gap-2 mt-2">
                {ATTACHMENT_STYLES.map((style) => (
                  <button
                    key={style.value}
                    onClick={() => setAttachmentStyle(attachmentStyle === style.value ? "" : style.value)}
                    className={`pill-btn ${attachmentStyle === style.value ? "pill-btn-active" : ""}`}
                  >
                    {style.label}
                  </button>
                ))}
              </div>
              {!isAuthenticated && (
                <p className="text-xs text-muted-foreground mt-2">
                  <a href={getLoginUrl()} className="text-primary hover:underline">Sign in</a> to save your attachment style and track your history.
                </p>
              )}
            </div>

            {/* Submit */}
            <button
              onClick={handleAnalyze}
              disabled={loading || !message.trim()}
              className="holdoff-btn w-full"
            >
              {loading ? (
                <span className="flex items-center justify-center gap-2">
                  <span className="animate-spin w-4 h-4 border-2 border-white/30 border-t-white rounded-full inline-block" />
                  Analyzing...
                </span>
              ) : (
                "Get my verdict"
              )}
            </button>

            {!isAuthenticated && (
              <p className="text-center text-xs text-muted-foreground">
                3 free verdicts. <a href={getLoginUrl()} className="text-primary hover:underline">Sign in</a> to save history.
              </p>
            )}
          </div>
        ) : (
          <div className="space-y-4 animate-fade-in">
            {/* Verdict */}
            <div className={`holdoff-card border ${verdictConfig?.bg} text-center`}>
              <div className={`text-5xl font-display font-bold mb-2 ${verdictConfig?.text}`}>
                {verdictConfig?.icon}
              </div>
              <div className={`text-2xl font-display font-bold tracking-wider mb-3 ${verdictConfig?.text}`}>
                {result.verdict}
              </div>
              {result.patternName && (
                <div className="inline-block bg-white/5 rounded-full px-3 py-1 text-xs text-muted-foreground mb-3">
                  {result.patternName}
                </div>
              )}
              <p className="text-sm leading-relaxed text-foreground">{result.explanation}</p>
            </div>

            {/* Reframe */}
            {result.reframe && (
              <div className="holdoff-card border-primary/20 bg-primary/5">
                <p className="text-xs text-primary font-medium uppercase tracking-wider mb-1">What you're actually feeling</p>
                <p className="text-sm leading-relaxed italic text-foreground/80">{result.reframe}</p>
              </div>
            )}

            {/* Rewrite */}
            {result.rewrite && (
              <div className="holdoff-card">
                <p className="text-xs text-muted-foreground font-medium uppercase tracking-wider mb-2">A better version</p>
                <p className="text-sm leading-relaxed text-foreground">{result.rewrite}</p>
                <button
                  onClick={() => {
                    navigator.clipboard.writeText(result.rewrite!);
                    toast.success("Copied to clipboard");
                  }}
                  className="mt-3 text-xs text-primary hover:underline"
                >
                  Copy this version
                </button>
              </div>
            )}

            {/* Actions */}
            <div className="flex gap-3">
              <button onClick={handleReset} className="holdoff-btn-secondary flex-1">
                Try another
              </button>
              <Link href="/companions/sadie" className="holdoff-btn flex-1 text-center">
                Talk to Sadie ✨
              </Link>
            </div>

            {/* History link */}
            {isAuthenticated && (
              <Link href="/chronicle" className="block text-center text-xs text-muted-foreground hover:text-foreground transition-colors">
                View your verdict history →
              </Link>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
