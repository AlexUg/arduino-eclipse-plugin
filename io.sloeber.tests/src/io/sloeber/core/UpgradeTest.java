package io.sloeber.core;

import static org.junit.Assert.fail;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.junit.Test;

/**
 * this test assumes it is stared in a clean workspace and not in the UI thread
 * 
 * this test copies previous versions sloeber projects into the new workspace
 * opens them and builds them if the build is successful the test is considered
 * successful
 * 
 * @author jan
 *
 */
@SuppressWarnings({ "nls", "static-method" })
public class UpgradeTest {
    /**
     * Upgrade a single config project
     * 
     * @throws Exception
     */

    @Test
    public void upgradeSingleConfigProjectFromVersion4_3_3() throws Exception {

        String projectName = "upgradeSingleConfigProject";
        String inputZipFile = Shared.getprojectZip("upgradeSingleConfigProject4_3_3.zip").toOSString();
        // /io.sloeber.tests/src/projects/upgradeSingleConfigProject4_3_3.zip
        final IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IWorkspaceRoot root = workspace.getRoot();
        IProject theTestProject = root.getProject(projectName);
        Shared.unzip(inputZipFile, root.getLocation().toOSString());
        theTestProject.create(null);
        theTestProject.open(null);
        Shared.waitForAllJobsToFinish(); // for the indexer
        theTestProject.build(IncrementalProjectBuilder.FULL_BUILD, null);
        if (Shared.hasBuildErrors(theTestProject)) {
            fail("Failed to compile the project:" + projectName);
        }
    }

    /**
     * Upgrade a triple dual project
     * 
     * @throws Exception
     */
    @Test
    public void upgradeDualConfigProject() throws Exception {

    }

    /**
     * Upgrade a triple config project
     * 
     * @throws Exception
     */
    @Test
    public void upgradeTripleConfigProject() throws Exception {

    }

}
