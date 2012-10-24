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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/23/12
 * Time: 7:23 PM
 */
public class SvnEditCommitMessageAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dc = e.getDataContext();
    final ChangeList[] lists = VcsDataKeys.CHANGE_LISTS.getData(dc);
    final boolean enabled = lists != null && lists.length == 1 && lists[0] instanceof SvnChangeList;
    if (! enabled) return;
    final SvnChangeList svnList = (SvnChangeList) lists[0];
    Project project = PlatformDataKeys.PROJECT.getData(dc);
    project = project == null ? ProjectManager.getInstance().getDefaultProject() : project;

    final String edited = Messages.showMultilineInputDialog(project, "Attention! Previous message will be lost!\n\nNew revision comment:",
      "Edit Revision # " + svnList.getNumber() + " Comment", svnList.getComment(), Messages.getInformationIcon(), null);
    if (edited == null || edited.trim().equals(svnList.getComment().trim())) return;
    final Runnable listener = VcsDataKeys.REMOTE_HISTORY_CHANGED_LISTENER.getData(dc);
    ProgressManager.getInstance().run(new EditMessageTask(project, edited, svnList, listener));
  }

  @Override
  public void update(AnActionEvent e) {
    final DataContext dc = e.getDataContext();
    final ChangeList[] lists = VcsDataKeys.CHANGE_LISTS.getData(dc);
    final boolean enabled = lists != null && lists.length == 1 && lists[0] instanceof SvnChangeList;
    boolean visible = enabled;
    Project project = PlatformDataKeys.PROJECT.getData(dc);
    if (project == null) {
      visible = VcsDataKeys.REMOTE_HISTORY_LOCATION.getData(dc) instanceof SvnRepositoryLocation;
    } else {
      visible = ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(SvnVcs.VCS_NAME);
    }
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(enabled);
  }

  /*private boolean anyChangeUnderSvn(ChangeList[] lists) {
    for (ChangeList list : lists) {
      final Collection<Change> changes = list.getChanges();
      for (Change change : changes) {
        if (isSvn(change.getBeforeRevision()) || isSvn(change.getAfterRevision())) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isSvn(ContentRevision cr) {
    return cr instanceof MarkerVcsContentRevision && SvnVcs.getKey().equals(((MarkerVcsContentRevision) cr).getVcsKey());
  }*/

  private static class EditMessageTask extends Task.Backgroundable {
    private final String myNewMessage;
    private final SvnChangeList myChangeList;
    private final Runnable myListener;
    private VcsException myException;
    private final SvnVcs myVcs;

    private EditMessageTask(@Nullable Project project, final String newMessage, final SvnChangeList changeList, Runnable listener) {
      super(project, "Edit Revision Comment");
      myNewMessage = newMessage;
      myChangeList = changeList;
      myListener = listener;
      myVcs = SvnVcs.getInstance(myProject);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      final SVNWCClient client = myVcs.createWCClient();
      final String url = myChangeList.getLocation().getURL();
      final SVNURL root;
      try {
        root = SvnUtil.getRepositoryRoot(myVcs, SVNURL.parseURIEncoded(url));
        if (root == null) {
          myException = new VcsException("Can not determine repository root for URL: " + url);
          return;
        }
        client.doSetRevisionProperty(root, SVNRevision.create(myChangeList.getNumber()), "svn:log",
                                     SVNPropertyValue.create(myNewMessage), false, null);
      }
      catch (SVNException e) {
        myException = new VcsException(e);
      }
    }

    @Override
    public void onSuccess() {
      if (myException != null) {
        AbstractVcsHelper.getInstance(myProject).showError(myException, myTitle);
      } else {
        if (myListener != null) {
          myListener.run();
        }
        if (! myProject.isDefault()) {
          CommittedChangesCache.getInstance(myProject).commitMessageChanged(myVcs,
                                                                            myChangeList.getLocation(), myChangeList.getNumber(), myNewMessage);
        }
        VcsBalloonProblemNotifier.showOverChangesView(myProject, "Revision #" + myChangeList.getNumber() + " comment " +
                                                                 "changed to:\n'" + myNewMessage + "'", MessageType.INFO);
      }
    }
  }
}
