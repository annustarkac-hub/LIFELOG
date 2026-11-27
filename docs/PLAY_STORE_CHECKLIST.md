# Play Store Checklist (owner actions)

- [ ] Confirm application ID `com.lifelog.app` (permanent once published)
- [ ] Enroll in Play App Signing; upload `dist/LIFELOG-1.0.0-release.aab` signed with your upload key (the generated key can serve as upload key)
- [ ] Usage-access / sensitive-permission declaration for PACKAGE_USAGE_STATS, with a short video of the onboarding flow
- [ ] Data safety form: App activity → App interactions; collected, **not shared**, not transmitted, processed on device; deletion available in-app
- [ ] Host `docs/PRIVACY_POLICY.md` at a public URL and add a contact address
- [ ] Store listing must not promise full history since purchase or "all phone activity"
- [ ] Content rating questionnaire; target audience 18+ recommended
- [ ] Screenshots from a real device (Robolectric renders in docs/ are development evidence only)
- [ ] Test on ≥ 2 physical devices (QA_REPORT.md §2/§3) before production rollout
