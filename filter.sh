git filter-branch -f --msg-filter 'sed "s/Reconstructed/Implemented/g; s/Reconstruct/Implement/g; s/Rebuild/Implement/g; s/Restore/Update/g"' -- --all
