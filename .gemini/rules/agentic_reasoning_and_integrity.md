# Agentic Reasoning, Code Integrity & Self-Verification Rule

## 1. Multi-Step Reasoning & Chain Verification
- **Full Call Chain Inspection**: Before editing code, trace all caller/callee relationships across multi-file dependencies. Never edit in isolation without understanding upstream and downstream impacts.
- **Systematic & Atomic Edits**: Perform code edits in tightly controlled, atomic steps. Never rush one-shot implementations across multiple complex files without step-by-step verification.

## 2. Non-Destructive Refactoring & Code Completeness
- **Zero Code Laziness**: Never output truncated snippets, placeholders (`// TODO: rest of code`), or drop functional code blocks when editing files.
- **Preserve Existing Functionality**: Edits must strictly build upon existing features. Never modify, simplify, or break unrelated components or user settings unless explicitly instructed.

## 3. No Confirmation Bias & Empirical Self-Repair
- **Evidence-Driven Diagnosis**: Base all bug fixes on un-truncated logs and empirical tracebacks. Never work backward from a pre-conceived single guess.
- **Aggressive Recovery**: When a build or command fails, analyze the root cause immediately and fix the underlying issue. Never mask errors with silent fallbacks or swallow exceptions.
