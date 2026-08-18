# Privacy Policy & Privacy Governance

**Last Updated:** August 11, 2026  
**Developer / Owner:** NB Designs  
**Application:** Intelligent Search  

---

## 1. Overview & Commitment to User Privacy
NB Designs is committed to strict privacy protections for both the **Intelligent Search** application and the underlying repository codebase. Intelligent Search operates on an **On-Device First Privacy Architecture**.

## 2. On-Device Data Processing
Intelligent Search performs search queries, fuzzy taxonomy matching, contacts indexing, calendar event retrieval, and shortcut resolution **100% locally on your device**.

- **Zero Data Collection**: No search history, query logs, contacts, calendar events, app usage statistics, or user metrics are ever uploaded, transmitted, sold, or shared with external servers or third parties.
- **Search History Storage**: Search history is stored exclusively in an encrypted local SQLite database (`IntelligentSearchDatabase`) managed via Android Room on your physical device. Users can clear or disable search history at any time via **Settings -> Search Sources -> Web -> Search History**.
- **Calendar & Contacts Access**: Calendar and contacts permissions (`READ_CALENDAR`, `READ_CONTACTS`) are queried strictly on-demand for local search display and are never stored outside the active search session.

## 3. Web Search & External Engines
When performing web searches or fetching autocomplete web suggestions:
- Web queries are sent directly to your selected search provider (Google, DuckDuckGo, Bing, or your configured Custom Search URL).
- No intermediate server or tracking proxy captures your queries.

## 4. Code Base & Repository Privacy
- **Proprietary Software**: The repository, source code, build scripts, and visual assets are proprietary intellectual property of NB Designs.
- **Strict Non-Disclosure**: Unauthorized copying, distribution, or public disclosure of any source files or binaries is strictly prohibited under the project's Proprietary License.

## 5. Closed Beta Testing Email Governance
For users who voluntarily sign up to participate in the **Google Play Store Closed Beta Testing Track**:
- **Single-Purpose Collection**: Email addresses collected via the Closed Beta Signup form are used **exclusively** to import permissions into the Google Play Console testing list for your Google Play account.
- **No Marketing & No Third-Party Sharing**: NB Designs will **never** send promotional marketing emails, newsletters, or advertisements. Email addresses are never sold, rented, shared, or transferred to any third party.

## 6. Contact Information
For any questions regarding privacy governance or security protocols, please contact:  
**NB Designs Support**: [support.nbdesigns@gmail.com](mailto:support.nbdesigns@gmail.com)
