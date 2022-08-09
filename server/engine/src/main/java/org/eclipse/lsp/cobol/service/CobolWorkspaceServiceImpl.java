/*
 * Copyright (c) 2020 Broadcom.
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
package org.eclipse.lsp.cobol.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp.cobol.core.messages.LocaleStore;
import org.eclipse.lsp.cobol.core.messages.LogLevelUtils;
import org.eclipse.lsp.cobol.core.model.ErrorCode;
import org.eclipse.lsp.cobol.domain.databus.api.DataBusBroker;
import org.eclipse.lsp.cobol.domain.databus.model.RunAnalysisEvent;
import org.eclipse.lsp.cobol.service.copybooks.CopybookNameService;
import org.eclipse.lsp.cobol.service.copybooks.CopybookService;
import org.eclipse.lsp.cobol.service.delegates.completions.Keywords;
import org.eclipse.lsp.cobol.service.utils.ShutdownCheckUtil;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.stream.Collectors.toList;
import static org.eclipse.lsp.cobol.core.model.ErrorCode.MISSING_COPYBOOK;
import static org.eclipse.lsp.cobol.service.utils.SettingsParametersEnum.*;

/**
 * This class is responsible to watch for any changes into the copybook folder and to fetch updated
 * settings coming from the client
 */
@Slf4j
@Singleton
public class CobolWorkspaceServiceImpl implements WorkspaceService {
  private final DataBusBroker dataBus;
  private final SettingsService settingsService;
  private final WatcherService watchingService;
  private final CopybookService copybookService;
  private final LocaleStore localeStore;
  private final SubroutineService subroutineService;
  private final ConfigurationService configurationService;
  private final DisposableLSPStateService disposableLSPStateService;
  private final CopybookNameService copybookNameService;
  private final Keywords keywords;

  @Inject
  public CobolWorkspaceServiceImpl(
      DataBusBroker dataBus,
      SettingsService settingsService,
      WatcherService watchingService,
      CopybookService copybookService,
      LocaleStore localeStore,
      SubroutineService subroutineService,
      ConfigurationService configurationService,
      DisposableLSPStateService disposableLSPStateService,
      CopybookNameService copybookNameService,
      Keywords keywords) {
    this.dataBus = dataBus;
    this.settingsService = settingsService;
    this.watchingService = watchingService;
    this.copybookService = copybookService;
    this.localeStore = localeStore;
    this.subroutineService = subroutineService;
    this.configurationService = configurationService;
    this.disposableLSPStateService = disposableLSPStateService;
    this.copybookNameService = copybookNameService;
    this.keywords = keywords;
  }

  /**
   * Execute a command generated by {@link CobolTextDocumentService#codeAction(CodeActionParams)}.
   * Return a WorkspaceEdit or null if no edits required. The list of supported commands depends on
   * {@link ErrorCode}.
   *
   * @param params - parameters of a command to be executed
   * @return a WorkspaceEdit or null if no edits required
   */
  @NonNull
  @Override
  public CompletableFuture<Object> executeCommand(@NonNull ExecuteCommandParams params) {
    if (!disposableLSPStateService.isServerShutdown()) {
      runAsync(executeCopybookFix(params)).whenComplete(reportExceptionIfFound(params));
    }

    return ShutdownCheckUtil.checkServerState(disposableLSPStateService);
  }

  private Runnable executeCopybookFix(@NonNull ExecuteCommandParams params) {
    return () -> {
      if (MISSING_COPYBOOK.getLabel().equals(params.getCommand())) {
        rerunAnalysis(true);
      }
    };
  }

  /**
   * Process changed configuration on the client state. This notification triggered automatically
   * when the user modify configuration settings in the client. Invalidate all the caches to avoid
   * dirty state.
   *
   * @param params - LSPSpecification -> The actual changed settings; Actually -> null all the time.
   */
  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {
    if (!disposableLSPStateService.isServerShutdown()) {
      copybookNameService.copybookLocalFolders().thenAccept(this::acceptSettingsChange);

      settingsService.fetchConfiguration(LOCALE.label).thenAccept(localeStore.notifyLocaleStore());
      settingsService
          .fetchConfiguration(LOGGING_LEVEL.label)
          .thenAccept(LogLevelUtils.updateLogLevel());
      configurationService.updateConfigurationFromSettings();
      copybookNameService.collectLocalCopybookNames();
      keywords.updateStorage();
      snippets.updateStorage();
    }
  }

  private void acceptSettingsChange(List<String> localFolders) {
    List<String> watchingFolders = watchingService.getWatchingFolders();

    updateWatchers(localFolders, watchingFolders);
    rerunAnalysis(false);
  }

  private void updateWatchers(List<String> newPaths, List<String> existingPaths) {
    watchingService.addWatchers(
        newPaths.stream().filter(it -> !existingPaths.contains(it)).collect(toList()));

    watchingService.removeWatchers(
        existingPaths.stream().filter(it -> !newPaths.contains(it)).collect(toList()));
  }

  /**
   * This method triggered when the user modifies the settings in the settings.json
   *
   * @param params the object that wrap the content changed by the user in the settings.json and
   *     sent from the client to the server.
   */
  @Override
  public void didChangeWatchedFiles(@NonNull DidChangeWatchedFilesParams params) {
    if (disposableLSPStateService.isServerShutdown()) return;
    copybookNameService.collectLocalCopybookNames();
    rerunAnalysis(false);
  }

  private void rerunAnalysis(boolean verbose) {
    copybookService.invalidateCache();
    subroutineService.invalidateCache();
    LOG.info("Cache invalidated");
    dataBus.postData(new RunAnalysisEvent(verbose));
  }

  @NonNull
  private BiConsumer<Object, Throwable> reportExceptionIfFound(
      @NonNull ExecuteCommandParams params) {
    return (res, ex) ->
        ofNullable(ex)
            .ifPresent(
                it ->
                    LOG.error("Cannot execute command " + params.getCommand() + ": " + params, ex));
  }
}
