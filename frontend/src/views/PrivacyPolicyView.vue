<script setup lang="ts">
import logoUrl from '@/assets/ChainlinkLogoTrResc.png'
</script>

<template>
  <div
    class="min-h-screen py-12 px-4"
    style="
      background: #2a7b9b;
      background: linear-gradient(
        45deg,
        rgba(42, 123, 155, 1) 0%,
        rgba(46, 130, 153, 1) 27%,
        rgba(87, 199, 133, 1) 73%,
        rgba(237, 221, 83, 1) 100%
      );
    "
  >
    <div class="max-w-2xl mx-auto">
      <div class="text-center mb-8">
        <router-link :to="{ name: 'login' }">
          <img :src="logoUrl" alt="LinkWeave" class="mx-auto h-20 w-20 mb-3" />
        </router-link>
        <h1 class="text-2xl font-bold tracking-tight text-white">Privacy Policy</h1>
        <p class="text-white/70 text-sm mt-1">Last updated: May 2025</p>
      </div>

      <div
        class="rounded-2xl p-8 space-y-6 text-white/90 text-sm leading-relaxed"
        style="
          background-color: rgba(30, 30, 35, 0.45);
          backdrop-filter: blur(20px);
          -webkit-backdrop-filter: blur(20px);
          border: 1px solid rgba(255, 255, 255, 0.15);
          box-shadow: 0 20px 40px -10px rgba(0, 0, 0, 0.5), inset 0 1px 1px 0 rgba(255, 255, 255, 0.08);
        "
      >
        <section>
          <h2 class="text-lg font-semibold text-white mb-2">Introduction</h2>
          <p>
            LinkWeave ("we", "our", "us") operates the LinkWeave browser extension (the "Extension") and the
            associated web application at
            <strong>chainlink.markushofstetter.com</strong> (the "Service"). This privacy policy describes how the
            Extension and Service collect, use, and share user data.
          </p>
        </section>

        <section>
          <h2 class="text-lg font-semibold text-white mb-2">Data Controller</h2>
          <p>
            LinkWeave is a self-hosted bookmark manager. The instance you connect to may be operated by you, your
            organisation, or a third party. The operator of the LinkWeave server instance is the data controller for
            the data stored on that server. This policy covers the data handled by the official LinkWeave Extension as
            published on the Chrome Web Store.
          </p>
        </section>

        <section>
          <h2 class="text-lg font-semibold text-white mb-2">What Data We Collect</h2>

          <h3 class="font-semibold text-white mt-4 mb-1">Data You Provide</h3>
          <ul class="list-disc list-inside space-y-1 ml-2">
            <li><strong>Account information:</strong> Email address, first name, last name, and password when you register. If you sign in with Google, your Google profile name and email are retrieved via OpenID Connect.</li>
            <li><strong>Bookmarks:</strong> URL, title, description, tags, and folder assignment for each bookmark you choose to save.</li>
            <li><strong>Collections and folders:</strong> Names and organisational structure you create to manage your bookmarks.</li>
          </ul>

          <h3 class="font-semibold text-white mt-4 mb-1">Data Collected Automatically</h3>
          <ul class="list-disc list-inside space-y-1 ml-2">
            <li><strong>Current page URL and title:</strong> When you open the Extension popup or use the context menu, the Extension reads the URL and title of the active browser tab using the <code>activeTab</code> and <code>tabs</code> permissions. This data is used solely to pre-fill the bookmark save form and is not transmitted unless you explicitly save the bookmark.</li>
            <li><strong>Clicked link URL:</strong> If you right-click a link and select "Add to LinkWeave", the URL of that link is captured to pre-fill the save form.</li>
            <li><strong>Click tracking:</strong> When you click a bookmark in the Extension, an anonymous count is incremented on the server to track how often a bookmark has been opened. No personal data is attached to these counts.</li>
          </ul>

          <h3 class="font-semibold text-white mt-4 mb-1">Local Storage</h3>
          <ul class="list-disc list-inside space-y-1 ml-2">
            <li><strong>Extension configuration:</strong> The API URL and Web App URL you configure in the Extension options page are stored in <code>chrome.storage.sync</code>. These settings remain on your device and are not transmitted to any server except the one you explicitly configure.</li>
            <li><strong>Temporary context menu data:</strong> The URL captured from the context menu is temporarily stored in <code>chrome.storage.local</code> and cleared when the popup opens.</li>
            <li><strong>Session cookies:</strong> Authentication cookies are stored in your browser to maintain your signed-in session with the LinkWeave API server.</li>
          </ul>
        </section>

        <section>
          <h2 class="text-lg font-semibold text-white mb-2">How We Use Your Data</h2>
          <ul class="list-disc list-inside space-y-1 ml-2">
            <li>To provide, maintain, and improve the bookmark management Service.</li>
            <li>To authenticate you and secure access to your data.</li>
            <li>To pre-fill bookmark information from the page you are currently viewing.</li>
            <li>To provide usage statistics (bookmark click counts) within your own account.</li>
          </ul>
        </section>

        <section>
          <h2 class="text-lg font-semibold text-white mb-2">How Your Data Is Shared</h2>
          <ul class="list-disc list-inside space-y-1 ml-2">
            <li><strong>Google:</strong> If you use "Sign in with Google", your authentication request is processed by Google. Google receives your sign-in event but does not receive your bookmark data. The scopes requested are <code>openid</code>, <code>profile</code>, and <code>email</code>.</li>
            <li><strong>Your configured server:</strong> All bookmark data you save is sent exclusively to the LinkWeave API server you have configured in the Extension options. By default, this is <strong>chainlink.markushofstetter.com</strong>. No data is sent to any other server.</li>
          </ul>
          <p class="mt-2">We do <strong>not</strong> sell, rent, or trade your data to any third party. We do not use advertising trackers or analytics services.</p>
        </section>

        <section>
          <h2 class="text-lg font-semibold text-white mb-2">Data Retention</h2>
          <p>
            Your data is retained for as long as your account exists. You can delete individual bookmarks, collections,
            or your entire account at any time through the Service. Upon account deletion, all associated data is
            permanently removed from the server.
          </p>
        </section>

        <section>
          <h2 class="text-lg font-semibold text-white mb-2">Data Security</h2>
          <p>
            All data transmitted between the Extension and the API server is encrypted using HTTPS/TLS. Passwords are
            hashed before storage and are never stored in plain text. Access to your data requires authentication.
          </p>
        </section>

        <section>
          <h2 class="text-lg font-semibold text-white mb-2">Your Rights</h2>
          <p>Depending on your jurisdiction, you may have the right to:</p>
          <ul class="list-disc list-inside space-y-1 ml-2">
            <li>Access the personal data we hold about you.</li>
            <li>Request correction or deletion of your data.</li>
            <li>Export your bookmark data.</li>
            <li>Withdraw consent for data processing.</li>
          </ul>
          <p class="mt-2">To exercise any of these rights, you can manage your data directly through the Service or contact us using the details below.</p>
        </section>

        <section>
          <h2 class="text-lg font-semibold text-white mb-2">Children's Privacy</h2>
          <p>
            The Service is not directed at children under the age of 16. We do not knowingly collect personal data from
            children under 16.
          </p>
        </section>

        <section>
          <h2 class="text-lg font-semibold text-white mb-2">Changes to This Policy</h2>
          <p>
            We may update this privacy policy from time to time. Changes will be posted on this page with an updated
            "Last updated" date. Continued use of the Extension after changes constitutes acceptance of the updated
            policy.
          </p>
        </section>

        <div class="pt-4 border-t border-white/10 text-center">
          <router-link :to="{ name: 'login' }" class="text-white/60 hover:text-white text-sm underline">
            Back to LinkWeave
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>
