import os
import subprocess

commits = subprocess.check_output(['git', 'log', '--reverse', '--format=%H', 'temp_main']).decode().split()

# Check out the first commit
subprocess.check_call(['git', 'checkout', '--detach', commits[0]])
author = subprocess.check_output(['git', 'log', '-1', '--format=%an']).decode().strip()
if author == 'AI Agent':
    subprocess.check_call(['git', 'commit', '--amend', '--no-edit', '--author=NB Designs <support.nbdesigns@gmail.com>'])

# Cherry-pick the rest
for commit in commits[1:]:
    subprocess.check_call(['git', 'cherry-pick', commit])
    author = subprocess.check_output(['git', 'log', '-1', '--format=%an']).decode().strip()
    if author == 'AI Agent':
        subprocess.check_call(['git', 'commit', '--amend', '--no-edit', '--author=NB Designs <support.nbdesigns@gmail.com>'])

# Update branch
subprocess.check_call(['git', 'branch', '-f', 'temp_main', 'HEAD'])
