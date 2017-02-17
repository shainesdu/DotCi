/*
The MIT License (MIT)

Copyright (c) 2014, Groupon, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */
package com.groupon.jenkins.github;

import com.groupon.jenkins.dynamic.build.DynamicBuild;
import com.groupon.jenkins.github.services.GithubRepositoryService;
import hudson.Extension;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;

import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.model.Result.SUCCESS;
import static hudson.model.Result.UNSTABLE;

@Extension
public class CommitStatusUpdateRunListener extends RunListener<DynamicBuild> {

    private static final Logger LOGGER = Logger.getLogger(CommitStatusUpdateRunListener.class.getName());

    @Override
    public void onInitialize(final DynamicBuild build) {
        final GHRepository repository = getGithubRepository(build);

        try {
            String url = "";
            try {
                url = build.getFullUrl();
            } catch (final Exception e) {
                // do nothing
            }
            repository.createCommitStatus(build.getSha(), GHCommitState.PENDING, url, "Build in progress", getContext(build));
        } catch (final Exception e) {
            // Ignore if cannot create a pending status
            LOGGER.log(Level.WARNING, "Failed to Update commit status", e);
//            printErrorToBuildConsole(listener, e);
        }
    }

    private void printErrorToBuildConsole(final TaskListener listener, final Exception e) {
        listener.getLogger().println("Failed to update Commit status");
        listener.getLogger().println(ExceptionUtils.getStackTrace(e));
    }

    private String getContext(final DynamicBuild build) {
        return build.isPullRequest() ? "DotCi/PR" : "DotCi/push";
    }

    @Override
    public void onCompleted(final DynamicBuild build, final TaskListener listener) {
        final String sha1 = build.getSha();
        if (sha1 == null) {
            return;
        }

        final GHRepository repository = getGithubRepository(build);
        final GHCommitState state;
        String msg;
        final Result result = build.getResult();
        if (result.isBetterOrEqualTo(SUCCESS)) {
            state = GHCommitState.SUCCESS;
            msg = "Success";
        } else if (result.isBetterOrEqualTo(UNSTABLE)) {
            state = GHCommitState.FAILURE;
            msg = "Unstable";
        } else {
            state = GHCommitState.FAILURE;
            msg = "Failed";
        }
        if (build.isSkipped()) {
            msg += " - Skipped";
        }
        try {
            listener.getLogger().println("setting commit status on Github for " + repository.getHtmlUrl() + "/commit/" + sha1);
            repository.createCommitStatus(sha1, state, build.getFullUrl(), msg, getContext(build));
        } catch (final Exception e) {
            printErrorToBuildConsole(listener, e);
        }

    }

    protected GHRepository getGithubRepository(final DynamicBuild build) {
        return new GithubRepositoryService(build.getGithubRepoUrl()).getGithubRepository();
    }

}
