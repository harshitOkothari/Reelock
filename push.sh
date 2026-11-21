#!/bin/sh
# Run from inside the Reelock folder:  sh push.sh
# Pushes this project to https://github.com/harshitOkothari/Reelock (a new, empty repo with the same name as the app).
git config user.name "harshitOkothari"
git config user.email "harshitOkothari@users.noreply.github.com"
rm -rf .git
git init -q
git add -A
git commit -q -m "Reelock v2.4"
git branch -M main
git remote add origin https://github.com/harshitOkothari/Reelock.git
git push -u origin main
