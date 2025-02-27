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
package org.eclipse.lsp.cobol.common;

import org.eclipse.lsp.cobol.common.mapping.TextTransformations;

/**
 * Preprocessor that provides cleaning code functionality
 */
public interface CleanerPreprocessor {
  /**
   * Check and clean of the code as per cobol program structure.
   *
   * @param documentUri unique resource identifier of the processed document
   * @param cobolCode the content of the document that should be processed
   * @return modified code wrapped object and list of syntax error that might send back to the
   *     client
   */
  ResultWithErrors<TextTransformations> cleanUpCode(String documentUri, String cobolCode);
}
