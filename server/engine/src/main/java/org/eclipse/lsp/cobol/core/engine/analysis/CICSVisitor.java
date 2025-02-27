/*
 * Copyright (c) 2022 Broadcom.
 * The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Broadcom, Inc. - initial API and implementation
 *
 */

package org.eclipse.lsp.cobol.core.engine.analysis;

import com.google.common.collect.ImmutableList;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.RuleNode;
import org.eclipse.lsp.cobol.common.mapping.ExtendedSource;
import org.eclipse.lsp.cobol.common.model.Locality;
import org.eclipse.lsp.cobol.common.model.tree.Node;
import org.eclipse.lsp.cobol.common.model.tree.variable.QualifiedReferenceNode;
import org.eclipse.lsp.cobol.common.model.tree.variable.VariableUsageNode;
import org.eclipse.lsp.cobol.common.utils.RangeUtils;
import org.eclipse.lsp.cobol.core.CICSParserBaseVisitor;
import org.eclipse.lsp.cobol.core.model.tree.CodeBlockUsageNode;
import org.eclipse.lsp.cobol.core.model.tree.StopNode;
import org.eclipse.lsp.cobol.core.visitor.VisitorHelper;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.eclipse.lsp.cobol.core.CICSParser.*;

/**
 * This visitor analyzes the parser tree for CICS and returns its semantic context as a syntax tree
 */
@Slf4j
@AllArgsConstructor
class CICSVisitor extends CICSParserBaseVisitor<List<Node>> {

  private final Position position;
  private final String programUri;
  private final ExtendedSource extendedSource;

  @Override
  public List<Node> visitQualifiedDataName(QualifiedDataNameContext ctx) {
    return addTreeNode(ctx, QualifiedReferenceNode::new);
  }

  @Override
  public List<Node> visitDataName(DataNameContext ctx) {
    return addTreeNode(
        ctx, locality -> new VariableUsageNode(VisitorHelper.getName(ctx), locality));
  }

  @Override
  public List<Node> visitParagraphNameUsage(ParagraphNameUsageContext ctx) {
    String name = VisitorHelper.getName(ctx);
    Locality locality = VisitorHelper.buildNameRangeLocality(ctx, name, programUri);
    locality.setRange(RangeUtils.shiftRangeWithPosition(position, locality.getRange()));

    Location location = extendedSource.mapLocation(locality.getRange());

    Node node = new CodeBlockUsageNode(Locality.builder()
        .range(location.getRange())
        .uri(location.getUri())
        .build(), name);
    visitChildren(ctx).forEach(node::addChild);
    return ImmutableList.of(node);
  }

  // NOTE: Visitor is not managed by Guice DI, so can't use annotation here.
  @Override
  public List<Node> visitChildren(RuleNode node) {
    VisitorHelper.checkInterruption();
    return super.visitChildren(node);
  }

  @Override
  protected List<Node> defaultResult() {
    return ImmutableList.of();
  }

  @Override
  protected List<Node> aggregateResult(List<Node> aggregate, List<Node> nextResult) {
    return Stream.concat(aggregate.stream(), nextResult.stream()).collect(toList());
  }

  @Override
  public List<Node> visitCics_return(Cics_returnContext ctx) {
    return addTreeNode(ctx, StopNode::new);
  }

  private List<Node> addTreeNode(ParserRuleContext ctx, Function<Locality, Node> nodeConstructor) {
    Locality locality = VisitorHelper.buildNameRangeLocality(ctx, VisitorHelper.getName(ctx), programUri);
    locality.setRange(RangeUtils.shiftRangeWithPosition(position, locality.getRange()));

    Location location = extendedSource.mapLocation(locality.getRange());

    Node node = nodeConstructor.apply(Locality.builder()
            .range(location.getRange())
            .uri(location.getUri())
        .build());
    visitChildren(ctx).forEach(node::addChild);
    return ImmutableList.of(node);
  }
}
