/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.github;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OfficialActionVersionsTest {

    private static final String SHA_V1_0_0 = "1111111111111111111111111111111111111111";
    private static final String SHA_V2 = "2222222222222222222222222222222222222222";
    private static final String SHA_V3 = "3333333333333333333333333333333333333333";

    private static OfficialActionVersions versions() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("actions/checkout@v1.0.0", SHA_V1_0_0);
        props.put("actions/checkout@v1", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        props.put("actions/checkout@v2.0.0", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        props.put("actions/checkout@v2.0", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        props.put("actions/checkout@v2.1.0", "cccccccccccccccccccccccccccccccccccccccc");
        props.put("actions/checkout@v2.1", "cccccccccccccccccccccccccccccccccccccccc");
        props.put("actions/checkout@v2", SHA_V2);
        props.put("actions/checkout@v3.2.1", "dddddddddddddddddddddddddddddddddddddddd");
        props.put("actions/checkout@v3", SHA_V3);
        props.put("github/codeql-action/init@v3.1.0", "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
        props.put("github/codeql-action/init@v3", "ffffffffffffffffffffffffffffffffffffffff");
        props.put("codecov/codecov-action@v4", "0000000000000000000000000000000000000000");
        return OfficialActionVersions.fromProperties(props);
    }

    @Test
    void majorUpgradesToNewestMajorAcrossMajors() {
        // given
        OfficialActionVersions versions = versions();

        // when / then
        assertThat(versions.upgrade("actions/checkout", "v1")).isEqualTo("v3");
    }

    @Test
    void fullVersionUpgradesToNewestFullVersion() {
        // given
        OfficialActionVersions versions = versions();

        // when / then
        assertThat(versions.upgrade("actions/checkout", "v1.0.0")).isEqualTo("v3.2.1");
    }

    @Test
    void twoPartUpgradesToNewestTwoPart() {
        // given
        OfficialActionVersions versions = versions();

        // when / then
        assertThat(versions.upgrade("actions/checkout", "v2.0")).isEqualTo("v2.1");
    }

    @Test
    void commitShaUpgradesToLatestMajorSha() {
        // given
        OfficialActionVersions versions = versions();

        // when / then
        assertThat(versions.upgrade("actions/checkout", SHA_V1_0_0)).isEqualTo(SHA_V3);
    }

    @Test
    void preservesAbsentVPrefix() {
        // given
        OfficialActionVersions versions = versions();

        // when / then
        assertThat(versions.upgrade("actions/checkout", "1")).isEqualTo("3");
    }

    @Test
    void officialActionWithSubpathIsUpgraded() {
        // given
        OfficialActionVersions versions = versions();

        // when / then
        assertThat(versions.upgrade("github/codeql-action/init", "v3.0.0")).isEqualTo("v3.1.0");
    }

    @Test
    void doesNotDowngrade() {
        // given
        OfficialActionVersions versions = versions();

        // when / then
        assertThat(versions.upgrade("actions/checkout", "v9")).isNull();
    }

    @Test
    void alreadyLatestIsLeftUntouched() {
        // given
        OfficialActionVersions versions = versions();

        // when / then
        assertThat(versions.upgrade("actions/checkout", "v3")).isNull();
        assertThat(versions.upgrade("actions/checkout", "v3.2.1")).isNull();
    }

    @Test
    void commitShaAlreadyAtLatestMajorIsLeftUntouched() {
        // given
        OfficialActionVersions versions = versions();

        // when / then
        assertThat(versions.upgrade("actions/checkout", SHA_V3)).isNull();
    }

    @Test
    void thirdPartyActionsAreNotIndexed() {
        // given
        OfficialActionVersions versions = versions();

        // when / then
        assertThat(versions.upgrade("codecov/codecov-action", "v3")).isNull();
    }

    @Test
    void unknownOfficialActionIsLeftUntouched() {
        // given
        OfficialActionVersions versions = versions();

        // when / then
        assertThat(versions.upgrade("actions/not-a-real-action", "v1")).isNull();
    }

    @Test
    void branchAndPreReleaseRefsAreLeftUntouched() {
        // given
        OfficialActionVersions versions = versions();

        // when / then
        assertThat(versions.upgrade("actions/checkout", "main")).isNull();
        assertThat(versions.upgrade("actions/checkout", "v3.0.0-beta.1")).isNull();
    }

    @Test
    void isOfficialRecognizesActionsAndGithubOrgs() {
        // when / then
        assertThat(OfficialActionVersions.isOfficial("actions/checkout")).isTrue();
        assertThat(OfficialActionVersions.isOfficial("github/codeql-action/init")).isTrue();
        assertThat(OfficialActionVersions.isOfficial("codecov/codecov-action")).isFalse();
    }
}
