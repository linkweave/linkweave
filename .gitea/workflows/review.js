const https = require("https");
const fs = require("fs");

const diff = fs.readFileSync("/tmp/diff.txt", "utf8");
const apiKey = process.env.ZAI_API_KEY;

if (!apiKey) {
  console.error("ZAI_API_KEY not set");
  process.exit(1);
}

const systemPrompt =
  "You are a senior code reviewer for a Quarkus Java + Vue.js TypeScript project. " +
  "Review for: bugs, security issues, adherence to the 3-tier architecture " +
  "(Entity -> Repository -> Service -> Resource), proper use of AuthorizationService " +
  "in the Resource layer, correct DTO usage (Json suffix), and Flyway migration safety. " +
  "Skip style-only comments. Be concise and actionable. Format as markdown.";

const userPrompt = "Review these code changes:\n\n" + diff;

const body = JSON.stringify({
  model: "glm-4.7",
  messages: [
    { role: "system", content: systemPrompt },
    { role: "user", content: userPrompt },
  ],
});

const options = {
  hostname: "open.bigmodel.cn",
  path: "/api/paas/v4/chat/completions",
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    Authorization: "Bearer " + apiKey,
    "Content-Length": Buffer.byteLength(body),
  },
};

const req = https.request(options, (res) => {
  let data = "";
  res.on("data", (chunk) => {
    data += chunk;
  });
  res.on("end", () => {
    if (res.statusCode >= 200 && res.statusCode < 300) {
      try {
        const parsed = JSON.parse(data);
        const content =
          (parsed.choices &&
            parsed.choices[0] &&
            parsed.choices[0].message &&
            parsed.choices[0].message.content) ||
          "No review generated.";
        fs.writeFileSync("/tmp/review.md", content);
        console.log("Review generated successfully.");
      } catch (e) {
        console.error("Failed to parse API response:", e.message);
        fs.writeFileSync(
          "/tmp/review.md",
          "Error: Failed to parse AI response."
        );
        process.exit(1);
      }
    } else {
      console.error(
        "API error " + res.statusCode + ": " + data.slice(0, 500)
      );
      fs.writeFileSync(
        "/tmp/review.md",
        "Error: API returned status " + res.statusCode
      );
      process.exit(1);
    }
  });
});

req.on("error", (e) => {
  console.error("Request failed:", e.message);
  fs.writeFileSync("/tmp/review.md", "Error: " + e.message);
  process.exit(1);
});

req.setTimeout(300000, () => {
  req.destroy(new Error("Request timed out"));
});

req.write(body);
req.end();
