package com.openjiuwen.harness.subagents;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's package re-exports in
 * {@code openjiuwen/harness/subagents/__init__.py}.
 */
class SubagentsPackageTest {

    @Test
    void allMatchesPythonPackageExports() {
        assertThat(SubagentsPackage.ALL).containsExactly(
                "build_browser_agent_config",
                "build_code_agent_config",
                "build_research_agent_config",
                "build_verification_agent_config",
                "create_browser_agent",
                "create_code_agent",
                "create_research_agent",
                "create_verification_agent",
                "build_mobile_gui_agent_config",
                "create_mobile_gui_agent"
        );
        assertThat(SubagentsPackage.exports()).isEqualTo(SubagentsPackage.ALL);
    }

    @Test
    void getAttributeReturnsFunctionMarkersAndRejectsMissingNames() {
        for (String exportName : SubagentsPackage.ALL) {
            assertThat(SubagentsPackage.getAttribute(exportName)).isEqualTo(exportName);
        }

        assertThatThrownBy(() -> SubagentsPackage.getAttribute("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no attribute 'missing'");
    }

    @Test
    void buildConfigMethodsDelegateToAgentFactories() {
        Object model = new Object();

        assertThat(SubagentsPackage.buildBrowserAgentConfig(model).getFactoryName())
                .isEqualTo(BrowserAgentFactory.BROWSER_AGENT_FACTORY_NAME);
        assertThat(SubagentsPackage.buildCodeAgentConfig(model).getFactoryName())
                .isEqualTo(CodeAgentFactory.CODE_AGENT_FACTORY_NAME);
        assertThat(SubagentsPackage.buildResearchAgentConfig(model).getFactoryName())
                .isEqualTo(ResearchAgentFactory.RESEARCH_AGENT_FACTORY_NAME);
        assertThat(SubagentsPackage.buildVerificationAgentConfig(model).getFactoryName())
                .isEqualTo(VerificationAgentFactory.VERIFICATION_AGENT_FACTORY_NAME);
        assertThat(SubagentsPackage.buildMobileGuiAgentConfig(model).getFactoryName())
                .isEqualTo(MobileGuiAgentFactory.MOBILE_GUI_AGENT_FACTORY_NAME);
    }

    @Test
    void createMethodsDelegateToFactories() {
        List<String> names = List.of(
                SubagentsPackage.createCodeAgent("model").getCard().getName(),
                SubagentsPackage.createResearchAgent("model").getCard().getName(),
                SubagentsPackage.createVerificationAgent("model").getCard().getName()
        );

        assertThat(names).containsExactly("code_agent", "research_agent", "verification_agent");
    }
}
