/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.spellchecker.inspection;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.LiteralExpressionTokenizer;
import com.intellij.spellchecker.inspections.Splitter;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.testFramework.UsefulTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class LiteralExpressionTokenizerTest extends UsefulTestCase {
  private static class TokenCollector extends TokenConsumer {
    private List<String> myTokenTexts = new ArrayList<String>();

    @Override
    public void consumeToken(PsiElement element, String text, boolean useRename, int offset, TextRange rangeToCheck, Splitter splitter) {
      myTokenTexts.add(text);
    }

    public List<String> getTokenTexts() {
      return myTokenTexts;
    }
  }

  public void testEscapeSequences() {
    doTest("hello\\nworld", "hello", "world");
  }

  public void testEscapeSequences2() {
    doTest("\\nhello\\nworld\\n", "hello", "world");
  }

  private static void doTest(final String text, final String... expected) {
    TokenCollector collector = new TokenCollector();
    LiteralExpressionTokenizer.processTextWithEscapeSequences(null, text, collector);
    assertOrderedEquals(collector.getTokenTexts(), expected);
  }
}
