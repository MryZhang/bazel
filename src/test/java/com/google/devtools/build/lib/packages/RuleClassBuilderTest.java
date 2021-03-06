// Copyright 2015 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.packages;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.build.lib.packages.Attribute.attr;
import static com.google.devtools.build.lib.syntax.Type.BOOLEAN;
import static com.google.devtools.build.lib.syntax.Type.INTEGER;
import static com.google.devtools.build.lib.syntax.Type.STRING;
import static com.google.devtools.build.lib.syntax.Type.STRING_LIST;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.packages.RuleClass.Builder.RuleClassType;
import com.google.devtools.build.lib.packages.util.PackageLoadingTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the {@link RuleClass.Builder}.
 */
@RunWith(JUnit4.class)
public class RuleClassBuilderTest extends PackageLoadingTestCase {
  private static final RuleClass.ConfiguredTargetFactory<Object, Object>
      DUMMY_CONFIGURED_TARGET_FACTORY =
          new RuleClass.ConfiguredTargetFactory<Object, Object>() {
            @Override
            public Object create(Object ruleContext) throws InterruptedException {
              throw new IllegalStateException();
            }
          };

  @Test
  public void testRuleClassBuilderBasics() throws Exception {
    RuleClass ruleClassA =
        new RuleClass.Builder("ruleA", RuleClassType.NORMAL, false)
            .factory(DUMMY_CONFIGURED_TARGET_FACTORY)
            .add(attr("srcs", BuildType.LABEL_LIST).legacyAllowAnyFileType())
            .add(attr("tags", STRING_LIST))
            .add(attr("X", com.google.devtools.build.lib.syntax.Type.INTEGER).mandatory())
            .build();

    assertThat(ruleClassA.getName()).isEqualTo("ruleA");
    assertThat(ruleClassA.getAttributeCount()).isEqualTo(3);
    assertThat(ruleClassA.hasBinaryOutput()).isTrue();

    assertThat((int) ruleClassA.getAttributeIndex("srcs")).isEqualTo(0);
    assertThat(ruleClassA.getAttributeByName("srcs")).isEqualTo(ruleClassA.getAttribute(0));

    assertThat((int) ruleClassA.getAttributeIndex("tags")).isEqualTo(1);
    assertThat(ruleClassA.getAttributeByName("tags")).isEqualTo(ruleClassA.getAttribute(1));

    assertThat((int) ruleClassA.getAttributeIndex("X")).isEqualTo(2);
    assertThat(ruleClassA.getAttributeByName("X")).isEqualTo(ruleClassA.getAttribute(2));
  }

  @Test
  public void testRuleClassBuilderTestIsBinary() throws Exception {
    RuleClass ruleClassA =
        new RuleClass.Builder("rule_test", RuleClassType.TEST, false)
            .factory(DUMMY_CONFIGURED_TARGET_FACTORY)
            .add(attr("tags", STRING_LIST))
            .add(attr("size", STRING).value("medium"))
            .add(attr("timeout", STRING))
            .add(attr("flaky", BOOLEAN).value(false))
            .add(attr("shard_count", INTEGER).value(-1))
            .add(attr("local", BOOLEAN))
            .build();
    assertThat(ruleClassA.hasBinaryOutput()).isTrue();
  }

  @Test
  public void testRuleClassBuilderGenruleIsNotBinary() throws Exception {
    RuleClass ruleClassA =
        new RuleClass.Builder("ruleA", RuleClassType.NORMAL, false)
            .factory(DUMMY_CONFIGURED_TARGET_FACTORY)
            .setOutputToGenfiles()
            .add(attr("tags", STRING_LIST))
            .build();
    assertThat(ruleClassA.hasBinaryOutput()).isFalse();
  }

  @Test
  public void testRuleClassTestNameValidity() throws Exception {
    try {
      new RuleClass.Builder("ruleA", RuleClassType.TEST, false).build();
      fail();
    } catch (IllegalArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void testRuleClassNormalNameValidity() throws Exception {
    try {
      new RuleClass.Builder("ruleA_test", RuleClassType.NORMAL, false).build();
      fail();
    } catch (IllegalArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void testDuplicateAttribute() throws Exception {
    RuleClass.Builder builder =
        new RuleClass.Builder("ruleA", RuleClassType.NORMAL, false).add(attr("a", STRING));
    try {
      builder.add(attr("a", STRING));
      fail();
    } catch (IllegalStateException e) {
      // Expected exception.
    }
  }

  @Test
  public void testPropertiesOfAbstractRuleClass() throws Exception {
    try {
      new RuleClass.Builder("$ruleA", RuleClassType.ABSTRACT, false).setOutputToGenfiles();
      fail();
    } catch (IllegalStateException e) {
      // Expected exception.
    }

    try {
      new RuleClass.Builder("$ruleB", RuleClassType.ABSTRACT, false)
          .setImplicitOutputsFunction(null);
      fail();
    } catch (IllegalStateException e) {
      // Expected exception.
    }
  }

  @Test
  public void testDuplicateInheritedAttribute() throws Exception {
    RuleClass a =
        new RuleClass.Builder("ruleA", RuleClassType.NORMAL, false)
            .factory(DUMMY_CONFIGURED_TARGET_FACTORY)
            .add(attr("a", STRING).value("A"))
            .add(attr("tags", STRING_LIST))
            .build();
    RuleClass b =
        new RuleClass.Builder("ruleB", RuleClassType.NORMAL, false)
            .factory(DUMMY_CONFIGURED_TARGET_FACTORY)
            .add(attr("a", STRING).value("B"))
            .add(attr("tags", STRING_LIST))
            .build();
    try {
      // In case of multiple attribute inheritance the attributes must equal
      new RuleClass.Builder("ruleC", RuleClassType.NORMAL, false, a, b).build();
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage("Attribute a is inherited multiple times in ruleC ruleclass");
    }
  }

  @Test
  public void testRemoveAttribute() throws Exception {
    RuleClass a =
        new RuleClass.Builder("rule", RuleClassType.NORMAL, false)
            .factory(DUMMY_CONFIGURED_TARGET_FACTORY)
            .add(attr("a", STRING))
            .add(attr("b", STRING))
            .add(attr("tags", STRING_LIST))
            .build();
    RuleClass.Builder builder =
        new RuleClass.Builder("c", RuleClassType.NORMAL, false, a)
            .factory(DUMMY_CONFIGURED_TARGET_FACTORY);
    RuleClass c = builder.removeAttribute("a").add(attr("a", INTEGER)).removeAttribute("b").build();
    assertThat(c.hasAttr("a", STRING)).isFalse();
    assertThat(c.hasAttr("a", INTEGER)).isTrue();
    assertThat(c.hasAttr("b", STRING)).isFalse();

    try {
      builder.removeAttribute("c");
      fail();
    } catch (IllegalStateException e) {
      // Expected exception.
    }
  }

  @Test
  public void testRequiredToolchainsAreInherited() throws Exception {
    Label mockToolchainType = Label.parseAbsoluteUnchecked("//mock_toolchain_type");
    RuleClass parent =
        new RuleClass.Builder("$parent", RuleClassType.ABSTRACT, false)
            .add(attr("tags", STRING_LIST))
            .addRequiredToolchains(ImmutableList.of(mockToolchainType))
            .build();
    RuleClass child =
        new RuleClass.Builder("child", RuleClassType.NORMAL, false, parent)
            .factory(DUMMY_CONFIGURED_TARGET_FACTORY)
            .add(attr("attr", STRING))
            .build();
    assertThat(child.getRequiredToolchains()).contains(mockToolchainType);
  }
}
