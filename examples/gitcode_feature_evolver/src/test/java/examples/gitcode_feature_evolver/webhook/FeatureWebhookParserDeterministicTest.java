/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import examples.gitcode_feature_evolver.job.FeatureCommand;
import examples.gitcode_issue_evolver.webhook.GitCodeWebhookVerifier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Deterministic Issue, Note, PR, command, and HMAC parsing checks. */
public final class FeatureWebhookParserDeterministicTest {
    private FeatureWebhookParserDeterministicTest() {
    }

    /** Run all local Webhook contract checks. */
    public static void main(String[] args) throws Exception {
        testIssueEvent();
        testNoteAndCommand();
        testPullRequestEvent();
        testHmac();
        System.out.println("FeatureWebhookParserDeterministicTest: PASS");
    }

    private static void testIssueEvent() throws Exception {
        JsonNode payload = FeatureWebhookParser.parse("""
                {
                  "project":{"path_with_namespace":"openJiuwen/agent-core-java"},
                  "object_attributes":{"iid":77,"title":"New API","state":"open",
                    "action":"update","url":"https://gitcode/issues/77",
                    "labels":[{"name":"feature"}]},
                  "changes":{"labels":{"previous":[],"current":[{"name":"feature"}]}}
                }
                """.getBytes(StandardCharsets.UTF_8));
        FeatureWebhookParser.IssueEvent event = FeatureWebhookParser.issue(payload);
        require(event.repository().equals("openJiuwen/agent-core-java"),
                "Issue repository was not parsed");
        require(event.iid() == 77 && event.isOpen(), "Issue identity or open state was not parsed");
        require(event.activatesLabel("feature"), "exact newly added feature label was not activated");
        require(!event.activatesLabel("Feature"), "feature label matching was not case-sensitive");
    }

    private static void testNoteAndCommand() throws Exception {
        JsonNode payload = FeatureWebhookParser.parse("""
                {
                  "project":{"path_with_namespace":"openJiuwen/agent-core-java"},
                  "user":{"username":"release-approver"},
                  "issue":{"iid":77},
                  "object_attributes":{"id":9001,"note":"/feature approve r2 accepted",
                    "noteable_type":"Issue"}
                }
                """.getBytes(StandardCharsets.UTF_8));
        FeatureWebhookParser.NoteEvent note = FeatureWebhookParser.note(payload);
        require(note.isIssueNote(), "Note Hook target type was not parsed");
        require(note.commentId().equals("9001") && note.author().equals("release-approver"),
                "Note identity or author was not parsed");
        FeatureCommand.Parsed command = FeatureCommand.Action.parse(note.body());
        require(command.action() == FeatureCommand.Action.APPROVE_R2,
                "authenticated R2 command action was not parsed");
        require(command.reason().equals("accepted"), "command reason was not retained");
    }

    private static void testPullRequestEvent() throws Exception {
        JsonNode payload = FeatureWebhookParser.parse("""
                {
                  "project":{"path_with_namespace":"openJiuwen/agent-core-java"},
                  "object_attributes":{"iid":88,"state":"closed","action":"merge"}
                }
                """.getBytes(StandardCharsets.UTF_8));
        FeatureWebhookParser.PullRequestEvent event = FeatureWebhookParser.pullRequest(payload);
        require(event.number() == 88 && event.isMerged(),
                "merge action did not override the generic closed state");
        require(!event.isClosed(), "merged PR was also classified as unmerged closed");
    }

    private static void testHmac() throws Exception {
        byte[] body = "verified feature webhook".getBytes(StandardCharsets.UTF_8);
        String secret = "0123456789abcdef0123456789abcdef";
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "sha256=" + HexFormat.of().formatHex(hmac.doFinal(body));
        require(GitCodeWebhookVerifier.verify(body, signature, secret),
                "valid GitCode Webhook HMAC was rejected");
        require(!GitCodeWebhookVerifier.verify(body, signature + "00", secret),
                "invalid GitCode Webhook HMAC was accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
