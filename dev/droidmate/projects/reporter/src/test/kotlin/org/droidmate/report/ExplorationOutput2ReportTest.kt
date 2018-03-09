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
package org.droidmate.report

import org.droidmate.configuration.Configuration
import org.droidmate.dir
import org.droidmate.exploration.data_aggregators.IExplorationLog
import org.droidmate.fileNames
import org.droidmate.report.apk.ClickFrequencyTable
import org.droidmate.report.apk.WidgetSeenClickedTable
import org.droidmate.report.apk.ApiCountTable
import org.droidmate.tests.fixture_monitoredSer2
import org.droidmate.withFiles
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import java.nio.file.FileSystem
import java.nio.file.Path
import org.junit.Test

class ExplorationOutput2ReportTest {

    @Test
    fun dummy() {
        assert(true)
    }

    private fun getTestData(fs: FileSystem, cfg: Configuration): List<IExplorationLog> {
        val serExplOutput: Path = fixture_monitoredSer2
        val mockFsDirWithOutput: Path = fs.dir(cfg.droidmateOutputDir).withFiles(serExplOutput)

        return OutputDir(mockFsDirWithOutput).notEmptyExplorationOutput2
    }

    @Test
    fun `Report structure`() {
        val mockFs: FileSystem = mockFs()
        val cfg = Configuration.getDefault()

        val rawData = getTestData(mockFs, cfg)

        // Act
        // "includePlots" is set to false because plots require gnuplot, which does not work on mock file system used in this test.
        val report = AggregateStats()
        report.write(mockFs.dir(cfg.reportOutputDir), rawData)

        assertOnAggregateStatsDataStructure(report, mockFs.dir(cfg.reportOutputDir), rawData)
    }

    private fun assertOnAggregateStatsDataStructure(report: AggregateStats,
                                                    reportDir: Path,
                                                    rawData: List<IExplorationLog>) {

        val table = report.getTableData(rawData, report.getFilePath(reportDir)).table
        assertThat(table.rowKeySet().size, greaterThan(0))
        assertThat(table.columnKeySet(),
                with(AggregateStatsTable) {
                    hasItems(
                            headerApkName,
                            headerPackageName,
                            headerExplorationTimeInSeconds,
                            headerActionsCount,
                            headerResetActionsCount,
                            headerViewsSeenCount,
                            headerViewsClickedCount,
                            headerApisSeenCount,
                            headerEventApiPairsSeenCount,
                            headerException
                    )
                }

        )
    }

    @Test
    fun `ViewCount report`() {

        val mockFs: FileSystem = mockFs()
        val cfg = Configuration.getDefault()
        val serExplOutput: Path = fixture_monitoredSer2
        val mockFsDirWithOutput: Path = mockFs.dir(cfg.droidmateOutputDir).withFiles(serExplOutput)

        // Act
        val rawData = OutputDir(mockFsDirWithOutput).notEmptyExplorationOutput2

        val viewCountTables = rawData.map { WidgetSeenClickedTable(it) }
        viewCountTables.forEach {
            assertThat(it.rowKeySet().size, greaterThan(0))
            assertThat(it.columnKeySet(),
                    hasItems(
                            WidgetSeenClickedTable.headerTime,
                            WidgetSeenClickedTable.headerViewsSeen,
                            WidgetSeenClickedTable.headerViewsClicked
                    )
            )
        }

        val clickFrequencyTables = rawData.map { ClickFrequencyTable(it) }
        clickFrequencyTables.forEach {
            assertThat(it.rowKeySet().size, greaterThan(0))
            assertThat(it.columnKeySet(),
                    hasItems(
                            ClickFrequencyTable.headerNoOfClicks,
                            ClickFrequencyTable.headerViewsCount
                    )
            )
        }

        val apiCountTables = rawData.map { ApiCountTable(it) }
        apiCountTables.forEach {
            assertThat(it.rowKeySet().size, greaterThan(0))
            assertThat(it.columnKeySet(),
                    hasItems(
                            ApiCountTable.headerTime,
                            ApiCountTable.headerApisSeen,
                            ApiCountTable.headerApiEventsSeen
                    )
            )
        }
    }

    @Test
    fun `Summary report test`() {
        val mockFs: FileSystem = mockFs()
        val cfg = Configuration.getDefault()

        val rawData = getTestData(mockFs, cfg)

        // Act
        // "includePlots" is set to false because plots require gnuplot, which does not work on mock file system used in this test.
        val report = Summary()
        report.write(mockFs.dir(cfg.reportOutputDir), rawData)

        assert(mockFs.dir(cfg.reportOutputDir).fileNames.contains(report.fileName))
    }

    @Test
    fun `AggregateStats report test`() {
        val mockFs: FileSystem = mockFs()
        val cfg = Configuration.getDefault()

        val rawData = getTestData(mockFs, cfg)

        // Act
        // "includePlots" is set to false because plots require gnuplot, which does not work on mock file system used in this test.
        val report = AggregateStats()
        report.write(mockFs.dir(cfg.reportOutputDir), rawData)

        assert(mockFs.dir(cfg.reportOutputDir).fileNames.contains(report.getFilePath(mockFs.dir(cfg.reportOutputDir)).fileName.toString()))
    }
}