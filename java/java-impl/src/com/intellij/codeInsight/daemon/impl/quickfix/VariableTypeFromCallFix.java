/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 10-Jun-2010
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class VariableTypeFromCallFix implements IntentionAction {
  private final PsiType myExpressionType;
  private final PsiVariable myVar;

  public VariableTypeFromCallFix(PsiClassType type, PsiVariable var) {
    myExpressionType = type;
    myVar = var;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("fix.variable.type.text",
                                  myVar.getName(),
                                  myExpressionType.getCanonicalText());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.variable.type.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    final TypeMigrationRules rules = new TypeMigrationRules(TypeMigrationLabeler.getElementType(myVar));
    rules.setMigrationRootType(myExpressionType);
    rules.setBoundScope(PsiSearchHelper.SERVICE.getInstance(project).getUseScope(myVar));

    TypeMigrationProcessor.runHighlightingTypeMigration(project, editor, rules, myVar);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }


   public static void registerQuickFixActions(PsiMethodCallExpression methodCall, PsiExpressionList list, HighlightInfo highlightInfo) {
    final JavaResolveResult result = methodCall.getMethodExpression().advancedResolve(false);
    PsiMethod method = (PsiMethod) result.getElement();
    final PsiSubstitutor substitutor = result.getSubstitutor();
    PsiExpression[] expressions = list.getExpressions();
    if (method == null || method.getParameterList().getParametersCount() != expressions.length) return;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < expressions.length; i++) {
      final PsiExpression expression = expressions[i];
      PsiType expressionType = expression.getType();
      if (expressionType instanceof PsiPrimitiveType) {
        expressionType = ((PsiPrimitiveType)expressionType).getBoxedType(expression);
      }
      if (expressionType == null) continue;

      final PsiParameter parameter = parameters[i];
      final PsiType formalParamType = parameter.getType();
      final PsiType parameterType = substitutor.substitute(formalParamType);
      if (parameterType.isAssignableFrom(expressionType)) continue;

      final PsiExpression qualifierExpression = methodCall.getMethodExpression().getQualifierExpression();
      if (qualifierExpression instanceof PsiReferenceExpression) {
        final PsiElement resolved = ((PsiReferenceExpression)qualifierExpression).resolve();
        if (resolved instanceof PsiVariable) {
          final PsiType varType = ((PsiVariable)resolved).getType();
          final PsiClass varClass = PsiUtil.resolveClassInType(varType);
          final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(expression.getProject()).getResolveHelper();
          if (varClass != null) {
            final PsiSubstitutor psiSubstitutor = resolveHelper.inferTypeArguments(varClass.getTypeParameters(),
                                                                                   parameters,
                                                                                   expressions, PsiSubstitutor.EMPTY, resolved,
                                                                                   DefaultParameterTypeInferencePolicy.INSTANCE);
            final PsiClassType appropriateVarType = JavaPsiFacade.getElementFactory(expression.getProject()).createType(varClass, psiSubstitutor);
            if (!varType.equals(appropriateVarType)) {
              QuickFixAction.registerQuickFixAction(highlightInfo, new VariableTypeFromCallFix(appropriateVarType, (PsiVariable) resolved));
            }
            break;
          }
        }
      }
      registerParameterTypeChange(highlightInfo, method, expression, parameterType);
    }
  }

  private static void registerParameterTypeChange(HighlightInfo highlightInfo,
                                                  PsiMethod method,
                                                  PsiExpression expression,
                                                  PsiType parameterType) {
    if (expression instanceof PsiReferenceExpression) {
      final PsiManager manager = method.getManager();
      if (manager.isInProject(method)) {
        final PsiMethod[] superMethods = method.findDeepestSuperMethods();
        for (PsiMethod superMethod : superMethods) {
          if (!manager.isInProject(superMethod)) return;
        }
        final PsiElement resolve = ((PsiReferenceExpression)expression).resolve();
        if (resolve instanceof PsiVariable) {
          HighlightUtil.registerChangeVariableTypeFixes((PsiVariable)resolve, parameterType, highlightInfo);
        }
      }
    }
  }
}
