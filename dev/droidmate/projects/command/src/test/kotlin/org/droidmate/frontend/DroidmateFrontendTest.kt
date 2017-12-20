// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org
package org.droidmate.frontend

import com.google.common.base.Throwables
import com.konradjamrozik.createDirIfNotExists
import com.konradjamrozik.toList
import org.droidmate.command.DroidmateCommand
import org.droidmate.command.ExploreCommand
import org.droidmate.configuration.Configuration
import org.droidmate.configuration.ConfigurationBuilder
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.exploration.data_aggregators.IApkExplorationOutput2
import org.droidmate.exploration.strategy.ExplorationStrategy
import org.droidmate.misc.BuildConstants
import org.droidmate.misc.ThrowablesCollection
import org.droidmate.report.OutputDir
import org.droidmate.storage.Storage2
import org.droidmate.test_suite_categories.RequiresDevice
import org.droidmate.test_suite_categories.RequiresSimulator
import org.droidmate.test_tools.DroidmateTestCase
import org.droidmate.test_tools.android_sdk.AaptWrapperStub
import org.droidmate.test_tools.configuration.ConfigurationForTests
import org.droidmate.test_tools.device_simulation.AndroidDeviceSimulator
import org.droidmate.test_tools.device_simulation.DeviceSimulation
import org.droidmate.test_tools.device_simulation.IDeviceSimulation
import org.droidmate.test_tools.device_simulation.TimeGenerator
import org.droidmate.test_tools.exceptions.ExceptionSpec
import org.droidmate.test_tools.exceptions.ITestException
import org.droidmate.test_tools.filesystem.MockFileSystem
import org.droidmate.test_tools.tools.DeviceToolsMock
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class DroidmateFrontendTest : DroidmateTestCase() {
    /**
     * <p>
     * This test checks if DroidMate correctly handles complex failure scenario. This test runs on three apps mocks and on a
     * simulated device. The apps behave in the following way:
     *
     * </p><p>
     * The first one finishes exploration successfully, no exceptions get thrown. Exploration results get serialized.
     *
     * </p><p>
     * The second app is faulty, making it impossible to perform some exploration action on it. This results in an exception during
     * exploration loop. The exception gets wrapped into a collection of apk exploration exceptions
     * (the collection is itself an exception), to be reported by exception handler at the end of DroidMate's run.
     * Exploration results still get serialized.
     *
     * </p><p>
     * The installation of the third apk on the device finishes successfully. However, when the exploration of it starts to run,
     * the device fails altogether. Thus, before the proper exploration loop starts, the call to 'hasPackageInstalled' to the
     * device fails. In addition, no exploration results are obtained (because the loop didn't start) and so nothing gets
     * serialized. DroidMate then tries to recover by uninstalling the apk, which also fails, because device is unavailable. This
     * attempt suppresses the original exception. Now DroidMate tries to undeploy the device, including stopping uiautomator daemon,
     * but it also fails, suppressing the exception of apk undeployment (which also suppressed another exception:
     * the 'has package installed')
     *
     * </p><p>
     * In the end, both the set of apk exploration exceptions (having one exception, from the exploration of second app) and
     * the device undeployment exception (having suppressed exception having suppressed exception) both get wrapped into a throwable
     * collection, also being an exception. This exception is then passed to exception handler, which logs all the relevant
     * information to stderr and exceptions.txt.
     *
     * </p>
     */
    @Category(RequiresSimulator::class)
    @Test
    fun `Handles exploration and fatal device exceptions`() {
        val mockedFs = MockFileSystem(arrayListOf(
                "mock_1_noThrow_outputOk",
                "mock_2_throwBeforeLoop_outputNone",
                "mock_3_throwInLoop_outputPartial",
                "mock_4_throwsOnUndeps_outputOk",
                "mock_5_neverExplored_outputNone"))
        val apks = mockedFs.apks
        val apk1 = apks.single { it.fileName == "mock_1_noThrow_outputOk.apk" }
        val apk2 = apks.single { it.fileName == "mock_2_throwBeforeLoop_outputNone.apk" }
        val apk3 = apks.single { it.fileName == "mock_3_throwInLoop_outputPartial.apk" }
        val apk4 = apks.single { it.fileName == "mock_4_throwsOnUndeps_outputOk.apk" }

        val exceptionSpecs = arrayListOf(

                // Thrown during Exploration.run()->tryDeviceHasPackageInstalled()
                ExceptionSpec("hasPackageInstalled", apk2.packageName),

                // Thrown during Exploration.explorationLoop()->ResetAppExplorationAction.run()
                // The call index is 2 because 1st call is made to close 'app has stopped' dialog box before the exploration loop starts,
                // i.e. in org.droidmate.command.exploration.Exploration.tryWarnDeviceDisplaysHomeScreen
                ExceptionSpec("perform", apk3.packageName, /* call index */ 2),

                // Thrown during ApkDeployer.tryUndeployApk().
                // The call index is 2 because 1st call is made during ApkDeployer.tryReinstallApk
                // No more apks should be explored after this one, as this is an apk undeployment failure.
                ExceptionSpec("uninstallApk", apk4.packageName, /* call index */ 2),

                // Thrown during AndroidDeviceDeployer.tryTearDown()
                ExceptionSpec("closeConnection", apk4.packageName)
        )

        val expectedApkPackageNamesOfSer2FilesInOutputDir = arrayListOf(apk1.packageName, apk3.packageName, apk4.packageName)

        exploreOnSimulatorAndAssert(mockedFs, exceptionSpecs, expectedApkPackageNamesOfSer2FilesInOutputDir)
    }

    @Category(RequiresSimulator::class)
    @Test
    fun `Handles assertion error during exploration loop`() {
        val mockedFs = MockFileSystem(arrayListOf(
                "mock_1_throwsAssertInLoop_outputNone"))
        val apk1 = mockedFs.apks.single { it.fileName == "mock_1_throwsAssertInLoop_outputNone.apk" }

        val exceptionSpecs = arrayListOf(

                // Thrown during Exploration.explorationLoop()
                // Note that this is an AssertionError
                ExceptionSpec("perform", apk1.packageName, /* call index */ 3, /* throwsEx */ true, /* exceptionalReturnBool */ null, /* throwsAssertionError */ true))

        val expectedApkPackageNamesOfSer2FilesInOutputDir: List<String> = ArrayList()

        exploreOnSimulatorAndAssert(mockedFs, exceptionSpecs, expectedApkPackageNamesOfSer2FilesInOutputDir)
    }

    private fun exploreOnSimulatorAndAssert(
            mockedFs: MockFileSystem,
            exceptionSpecs: List<ExceptionSpec>,
            expectedApkPackageNamesOfSer2FilesInOutputDir: List<String>) {
        val cfg = ConfigurationForTests().withFileSystem(mockedFs.fs).get()
        val timeGenerator = TimeGenerator()
        val deviceToolsMock = DeviceToolsMock(
                cfg,
                AaptWrapperStub(mockedFs.apks),
                AndroidDeviceSimulator.build(timeGenerator, mockedFs.apks.map { it.packageName }, exceptionSpecs, /* unreliableSimulation */ true))

        val spy = ExceptionHandlerSpy()

        // Act
        val exitStatus = DroidmateFrontend.main(
                cfg.args,
                object : ICommandProvider {
                    override fun provide(cfg: Configuration): DroidmateCommand =
                            ExploreCommand.build(cfg, { ExplorationStrategy.build(cfg) }, timeGenerator, deviceToolsMock)
                },
                mockedFs.fs,
                spy
        )

        assert(exitStatus != 0)

        assert(spy.handledThrowable is ThrowablesCollection)
        assert(spy.getThrowables().size == exceptionSpecs.size)
        assert(spy.getThrowables().map { (Throwables.getRootCause(it) as ITestException).exceptionSpec } == exceptionSpecs)

        val outputDir = OutputDir(cfg.droidmateOutputDirPath).dir

        assertSer2FilesInDirAre(outputDir, expectedApkPackageNamesOfSer2FilesInOutputDir)
    }

    /**
     * <p>
     * This test runs DroidMate against a {@code AndroidDeviceSimulator}.
     * Because a device simulator is used, this test doesn't require a device (real or emulated) to be available.
     * Because no device is used, also no {@code Apk} is necessary.
     * Thus, an in-memory mock {@code FileSystem} is used.
     * The file system contains one apk stub to be used as input for the test.
     * An {@code AaptWrapper} stub is used to provide the apk stub metadata.
     * </p>
     */
    @Category(RequiresSimulator::class)
    @Test
    fun `Explores on a device simulator`() {
        val mockedFs = MockFileSystem(arrayListOf("mock_app1"))
        val cfg = ConfigurationForTests().withFileSystem(mockedFs.fs).get()
        val apks = mockedFs.apks
        val timeGenerator = TimeGenerator()
        val simulator = AndroidDeviceSimulator.build(
                timeGenerator, apks.map { it.packageName },
                /* exceptionsSpec */ ArrayList(), /* unreliableSimulation */ true)
        val deviceToolsMock = DeviceToolsMock(cfg, AaptWrapperStub(apks), simulator)

        // Act
        val exitStatus = DroidmateFrontend.main(
                cfg.args,
                object : ICommandProvider {
                    override fun provide(cfg: Configuration): DroidmateCommand =
                            ExploreCommand.build(cfg, { ExplorationStrategy.build(cfg) }, timeGenerator, deviceToolsMock)
                },
                mockedFs.fs,
                ExceptionHandler()
        )

        assert(exitStatus == 0)

        val expectedDeviceSimulation = simulator.currentSimulation
        val actualDeviceSimulation = getDeviceSimulation(cfg.droidmateOutputDirPath)
        actualDeviceSimulation.assertEqual(expectedDeviceSimulation!!)
    }

    @Category(RequiresDevice::class)
    @Test
    fun `Explores monitored apk on a real device api23`() {
        val args = ConfigurationForTests().forDevice().setArgs(arrayListOf(
                Configuration.pn_apksNames, "[$BuildConstants.monitored_inlined_apk_fixture_api23_name]",
                Configuration.pn_widgetIndexes, "[0, 1, 2, 2, 2]",
                Configuration.pn_androidApi, Configuration.api23)).get().args

        exploreOnRealDevice(args.toList(), Configuration.api23)

        assert(true)
    }

    @Category(RequiresDevice::class)
    @Test
    fun `Unpack SER files`() {
        // Parameters
        val dirStr = "output_device1"
        val outputStr = "raw_data"
        val fs = FileSystems.getDefault()

        // Setup output dir
        var outputDir = fs.getPath(dirStr, outputStr)
        outputDir.createDirIfNotExists()

        // Initialize storage
        val droidmateOutputDirPath = fs.getPath(dirStr)
        val storage2 = Storage2(droidmateOutputDirPath)

        // Process files
        Files.walk(droidmateOutputDirPath).forEach { file ->
            System.out.println(file + "")
            //if (((String)(file + "")).contains('de.wortundbildverlag.mobil.apotheke.ser2'))
            if (file.toString().contains(".ser2")) {
                outputDir = file.parent.resolve(outputStr)
                outputDir.createDirIfNotExists()

                // Get data
                val obj = storage2.deserialize(file) as IApkExplorationOutput2
                //val packageName = obj.apk.packageName

                // Create output dir
                //val newDir = fs.getPath(dirStr, outputStr, packageName)
                //newDir.deleteDir()
                //newDir.createDirIfNotExists()

                // For each action
                //for (int i = 15; i < obj.actRes.size(); ++i)
                (0 until obj.actRes.size).forEach { i ->
                    val newActionFile = file.parent.resolve(outputStr).resolve("action$i.txt")
                    val action = obj.actRes[i].getAction().toString()
                    Files.write(newActionFile, action.toByteArray())

                    val newResultFile = file.parent.resolve(outputStr).resolve("windowHierarchyDump$i.xml")
                    val result = if (obj.actRes[i].getResult().successful)
                        obj.actRes[i].getResult().guiSnapshot.windowHierarchyDump
                    else
                        ""
                    Files.write(newResultFile, result.toByteArray())
                }
            }
        }

        assert(true)
    }

    /**
     * <p>
     * This tests runs DroidMate against a device (real or emulator) and deploys on it a monitored apk fixture. It assumes the apk
     * fixture with appropriate name will be present in the read apks dirs.
     *
     * </p><p>
     * This test also assumes the fixture will have two widgets to be clicked, and it will first click the first one,
     * then the second one, then terminate the exploration.
     *
     * </p><p>
     * The test will make DroidMate output results to {@code BuildConstants.test_temp_dir_name}.
     * To ensure logs are also output there, run this test with VM arg of {@code -DlogsDir="temp_dir_for_tests/logs"}.
     * Note that {@code logsDir} is defined in {@code org.droidmate.logging.LogbackConstants.getLogsDirPath}.
     *
     * </p>
     */
    private fun exploreOnRealDevice(args: List<String>, api: String) {
        val outputDir = OutputDir(ConfigurationBuilder().build(args.toTypedArray()).droidmateOutputDirPath)
        outputDir.clearContents()

        // Act
        val exitStatus = DroidmateFrontend.main(args.toTypedArray(), /* commandProvider = */ null)

        assert(exitStatus == 0, { "Exit status != 0. Please inspect the run logs for details, including exception thrown" })

        val apkOut = outputDir.explorationOutput2.single()

        val apiLogs = apkOut.apiLogs
        if (api == Configuration.api23) {
            // Api logs' structure (Android 6):
            //  [0] Reset
            //  [1] API: OpenURL
            //  [2] API: CameraOpen (first time, open dialog)
            //  [3] Runtime dialog close (resume app)
            //  [4] API: CameraOpen
            //  [5] Launch activity 2
            //  [6] Terminate
            assert(apiLogs.size == 7)

            val resetAppApiLogs = apiLogs[0]
            val clickApiLogs = apiLogs[1]
            val openPermissionDialogApiLogs = apiLogs[2]
            val onResumeApiLogs = apiLogs[3]
            val cameraApiLogs = apiLogs[4]
            val launchActivity2Logs = apiLogs[5]
            val terminateAppApiLogs = apiLogs[6]

            assert(resetAppApiLogs.map { it.methodName }.isEmpty())
            assert(clickApiLogs.map { it.methodName } == arrayListOf("<init>", "openConnection"))
            assert(openPermissionDialogApiLogs.map { it.methodName }.isEmpty())
            assert(onResumeApiLogs.map { it.methodName }.isEmpty())
            assert(cameraApiLogs.map { it.methodName } == arrayListOf("open"))
            assert(launchActivity2Logs.map { it.methodName }.isEmpty())
            assert(terminateAppApiLogs.map { it.methodName }.isEmpty())
        } else throw UnexpectedIfElseFallthroughError()
    }

    private fun getDeviceSimulation(outputDirPath: Path): IDeviceSimulation {
        val apkOut = OutputDir(outputDirPath).notEmptyExplorationOutput2.single()
        return DeviceSimulation(apkOut)
    }

    private fun assertSer2FilesInDirAre(dir: Path, packageNames: List<String>): Boolean {
        val serFiles = Files.list(dir).filter { it.fileName.toString().endsWith(Storage2.ser2FileExt) }.toList()

        assert(serFiles.size == packageNames.size)
        packageNames.forEach { packageName ->
            assert(serFiles.any { it.fileName.toString().contains(packageName) })
        }

        return true
    }
}
