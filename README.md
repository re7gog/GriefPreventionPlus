# GriefPreventionPlus
I wanted to develop a GriefPrevention extension for cities (Towny-like), but the way GriefPrevention stores data limits extension possibilities. I changed the database structure and the way it stored data. GriefPreventionPlus offers better performances, data integrity, extension capabilities, it is currently for MC1.7.10 only and I applied all GriefPrevention's last updates!

Feedback are needed! If you found an issue, please report it!
Please use the "issues" page on Github!

## Download
All builds for my plugins can be found at this link: http://kaikk.net/mc/

###Installation
- If you've installed GriefPrevention, remove GriefPrevention jar from plugins folder
- Put GriefPreventionPlus jar into plugins folder

If an existing GriefPrevention database is found, a copy will migrate to GriefPreventionPlus.
Your GriefPrevention database won't be removed: you can rollback to GriefPrevention if you need!

###Major features
- GriefPreventionPlus's MC1.7.10 version contains last fixes from GriefPrevention's MC1.8 version!
- MySQL database is a requirement. Removed file based storage.
- Drastically improved database performances and reduced size: bigger servers will notice it!
- Overall speed improvements
- API improvements: all claims and subdivisions have an unique id
- Less waste of resources (RAM)
- Javadoc for extension developers! (planned)

####Notice
- GriefPrevention's extensions don't work with GriefPreventionPlus without some little change on the code. If needed, I will fork most important GriefPrevention's extension to make it work with GriefPreventionPlus! You can ask for it!
- GriefPrevention 10.6.2 commit is not applied. Untrust in top level claims won't untrust in subdivisions. You can remove a player from all your claims if you're not on a claim.
