# GriefPreventionPlus
I wanted to develop a GriefPrevention extension for cities (Towny-like), but the way GriefPrevention stores data limits extension possibilities. I changed the database structure and the way it stored data. GriefPreventionPlus offers better performances, data integrity, extension capabilities, it is currently supports MC1.7.10 and MC1.8.3. A partial support for MC1.6.4 is currently planned. I applied all GriefPrevention's last updates!

Feedback are needed! If you found an issue, please report it!
Please use the "issues" page on Github!

I suggest you to check my other plugins, like GriefPreventionPlus-Cities, that adds cities to your server! Check it at http://github.com/KaiKikuchi/GriefPreventionPlus-Cities

All DeVcoFTB's 1.7 modpacks run this plugin.
Join us at http://www.devcoftb.com !

## Download
All builds for my plugins can be found at this link: http://kaikk.net/mc/

###Installation
- If you've installed GriefPrevention, remove GriefPrevention jar from plugins folder
- Put GriefPreventionPlus jar into plugins folder

If an existing GriefPrevention database is found, a copy will migrate to GriefPreventionPlus.
Your GriefPrevention database won't be removed: you can rollback to GriefPrevention if you need!

If you're using GriefPrevention file based storage, read this: https://github.com/KaiKikuchi/GriefPreventionPlus/issues/11

###Major features
- Get GriefPrevention's last updates on your MC 1.7.10 server!
- MySQL database is a requirement. Removed file based storage.
- Drastically improved database performances and reduced size: bigger servers will notice it!
- Overall performance improvements
- Integrated item restrictor (/gppr): it block can block lots of ranged items/weapons too!
- API improvements: all claims and subdivisions have an unique id
- Less waste of resources (RAM)
- You can use the /claim [range] command to claim land around your position!
- Delete claims by id
- Log entry when a player on a claim is killed by another player
- Javadoc for extension developers! (planned)


####Notice
- GriefPrevention's extensions don't work with GriefPreventionPlus without some little change on the code. If needed, I will fork most important GriefPrevention's extension to make it work with GriefPreventionPlus! You can ask for it!
- GriefPrevention 10.6.2 commit is not applied. Untrust in top level claims won't untrust in subdivisions. You can remove a player from all your claims if you're not on a claim.

##Support my life!
I'm currently unemployed and I'm studying at University (Computer Science).
I'll be unable to continue my studies soon because I need money.
If you like this plugin and you run it fine on your server, please <a href='http://kaikk.net/mc/#donate'>consider a donation</a>!
Thank you very much!
