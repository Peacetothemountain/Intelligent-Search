---
description: Review what's on dev here, then ask the owner one question: merge it into main, yes or no
---

Find out whether `dev` should reach `main`, and ask the owner. Don't wait on anything: if something isn't ready, say so and
stop.

1. Run `git fetch origin`. Take the newest commit on `origin/dev`. If `origin/main` already contains it, say there's nothing
   to merge and stop.
2. Check CI on that commit with `gh run list --branch dev --commit <sha>`. If it's still running, say so and stop. If it
   failed, say what failed and stop.
3. Review `origin/main...<sha>` with `/code-review high`, after reading `MEMORY.md`, since locked and declined items aren't
   findings. Then try to disprove each finding from the code. A blocker is only what survives and is a crash or ANR, lost or
   exposed data, a wrong result for the user, a security hole, or a change to something off limits. Drop everything else
   without mentioning it.
4. If anything blocks, fix it all in one commit on `dev`, push, tell the owner what you fixed, and stop. Run this again once
   CI has finished on that commit.
5. Otherwise ask the owner one question with `AskUserQuestion`: "Merge these N changes into main?", with Yes and No. Say in
   a few plain lines what people will notice. That's all they need to read.
6. On Yes, run `git push origin <sha>:main`. On No, push nothing and leave `dev` as it is.
