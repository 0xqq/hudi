/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hudi.table;

import org.apache.hudi.avro.model.HoodieCleanMetadata;
import org.apache.hudi.avro.model.HoodieCompactionPlan;
import org.apache.hudi.avro.model.HoodieRestoreMetadata;
import org.apache.hudi.avro.model.HoodieRollbackMetadata;
import org.apache.hudi.avro.model.HoodieSavepointMetadata;
import org.apache.hudi.client.SparkTaskContextSupplier;
import org.apache.hudi.client.WriteStatus;
import org.apache.hudi.common.HoodieEngineContext;
import org.apache.hudi.common.HoodieSparkEngineContext;
import org.apache.hudi.common.model.HoodieBaseFile;
import org.apache.hudi.common.model.HoodieKey;
import org.apache.hudi.common.model.HoodieRecord;
import org.apache.hudi.common.model.HoodieRecordPayload;
import org.apache.hudi.common.table.HoodieTableMetaClient;
import org.apache.hudi.common.table.timeline.HoodieInstant;
import org.apache.hudi.common.table.timeline.HoodieTimeline;
import org.apache.hudi.common.util.Option;
import org.apache.hudi.common.util.collection.Pair;
import org.apache.hudi.config.HoodieWriteConfig;
import org.apache.hudi.exception.HoodieNotSupportedException;
import org.apache.hudi.exception.HoodieUpsertException;
import org.apache.hudi.index.HoodieIndex;
import org.apache.hudi.index.SparkHoodieIndex;
import org.apache.hudi.io.HoodieSortedMergeHandle;
import org.apache.hudi.io.HoodieSparkCreateHandle;
import org.apache.hudi.io.HoodieSparkMergeHandle;
import org.apache.hudi.table.action.HoodieWriteMetadata;
import org.apache.hudi.table.action.bootstrap.HoodieBootstrapWriteMetadata;
import org.apache.hudi.table.action.bootstrap.SparkBootstrapCommitActionExecutor;
import org.apache.hudi.table.action.clean.SparkCleanActionExecutor;
import org.apache.hudi.table.action.commit.SparkBulkInsertCommitActionExecutor;
import org.apache.hudi.table.action.commit.SparkBulkInsertPreppedCommitActionExecutor;
import org.apache.hudi.table.action.commit.SparkDeleteCommitActionExecutor;
import org.apache.hudi.table.action.commit.SparkInsertCommitActionExecutor;
import org.apache.hudi.table.action.commit.SparkInsertPreppedCommitActionExecutor;
import org.apache.hudi.table.action.commit.SparkMergeHelper;
import org.apache.hudi.table.action.commit.SparkUpsertCommitActionExecutor;
import org.apache.hudi.table.action.commit.SparkUpsertPreppedCommitActionExecutor;
import org.apache.hudi.table.action.restore.SparkCopyOnWriteRestoreActionExecutor;
import org.apache.hudi.table.action.rollback.SparkCopyOnWriteRollbackActionExecutor;
import org.apache.hudi.table.action.savepoint.SparkSavepointActionExecutor;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HoodieSparkCopyOnWriteTable<T extends HoodieRecordPayload> extends HoodieSparkTable<T> {
  private static final Logger LOG = LogManager.getLogger(HoodieSparkCopyOnWriteTable.class);

  public HoodieSparkCopyOnWriteTable(HoodieWriteConfig config, HoodieEngineContext context, HoodieTableMetaClient metaClient) {
    super(config, context, metaClient);
  }

  @Override
  public HoodieWriteMetadata<JavaRDD<WriteStatus>> upsert(HoodieEngineContext context, String instantTime, JavaRDD<HoodieRecord<T>> records) {
    return new SparkUpsertCommitActionExecutor<>((HoodieSparkEngineContext) context, config, this, instantTime, records).execute();
  }

  @Override
  public HoodieWriteMetadata<JavaRDD<WriteStatus>> insert(HoodieEngineContext context, String instantTime, JavaRDD<HoodieRecord<T>> records) {
    return new SparkInsertCommitActionExecutor<>((HoodieSparkEngineContext) context, config, this, instantTime, records).execute();
  }

  @Override
  public HoodieWriteMetadata<JavaRDD<WriteStatus>> bulkInsert(HoodieEngineContext context, String instantTime, JavaRDD<HoodieRecord<T>> records, Option<BulkInsertPartitioner<JavaRDD<HoodieRecord<T>>>> bulkInsertPartitioner) {
    return new SparkBulkInsertCommitActionExecutor((HoodieSparkEngineContext) context, config,
        this, instantTime, records, bulkInsertPartitioner).execute();
  }

  @Override
  public HoodieWriteMetadata<JavaRDD<WriteStatus>> delete(HoodieEngineContext context, String instantTime, JavaRDD<HoodieKey> keys) {
    return new SparkDeleteCommitActionExecutor<>((HoodieSparkEngineContext) context, config, this, instantTime, keys).execute();
  }

  @Override
  public HoodieWriteMetadata<JavaRDD<WriteStatus>> upsertPrepped(HoodieEngineContext context, String instantTime, JavaRDD<HoodieRecord<T>> preppedRecords) {
    return new SparkUpsertPreppedCommitActionExecutor<>((HoodieSparkEngineContext) context, config, this, instantTime, preppedRecords).execute();
  }

  @Override
  public HoodieWriteMetadata<JavaRDD<WriteStatus>> insertPrepped(HoodieEngineContext context, String instantTime, JavaRDD<HoodieRecord<T>> preppedRecords) {
    return new SparkInsertPreppedCommitActionExecutor<>((HoodieSparkEngineContext) context, config, this, instantTime, preppedRecords).execute();
  }

  @Override
  public HoodieWriteMetadata<JavaRDD<WriteStatus>> bulkInsertPrepped(HoodieEngineContext context, String instantTime, JavaRDD<HoodieRecord<T>> preppedRecords, Option<BulkInsertPartitioner<JavaRDD<HoodieRecord<T>>>> bulkInsertPartitioner) {
    return new SparkBulkInsertPreppedCommitActionExecutor((HoodieSparkEngineContext) context, config,
        this, instantTime, preppedRecords, bulkInsertPartitioner).execute();
  }

  @Override
  public HoodieIndex<T, JavaRDD<HoodieRecord<T>>, JavaRDD<HoodieKey>, JavaRDD<WriteStatus>, JavaPairRDD<HoodieKey, Option<Pair<String, String>>>> getIndex(HoodieWriteConfig config) {
    return SparkHoodieIndex.createIndex(config);
  }

  @Override
  public Option<HoodieCompactionPlan> scheduleCompaction(HoodieEngineContext context, String instantTime, Option<Map<String, String>> extraMetadata) {
    throw new HoodieNotSupportedException("Compaction is not supported on a CopyOnWrite table");
  }

  @Override
  public HoodieWriteMetadata<JavaRDD<WriteStatus>> compact(HoodieEngineContext context, String compactionInstantTime) {
    throw new HoodieNotSupportedException("Compaction is not supported on a CopyOnWrite table");
  }

  @Override
  public HoodieBootstrapWriteMetadata bootstrap(HoodieEngineContext context, Option<Map<String, String>> extraMetadata) {
    return new SparkBootstrapCommitActionExecutor((HoodieSparkEngineContext) context, config, this, extraMetadata).execute();
  }

  @Override
  public void rollbackBootstrap(HoodieEngineContext context, String instantTime) {
    new SparkCopyOnWriteRestoreActionExecutor((HoodieSparkEngineContext) context, config, this, instantTime, HoodieTimeline.INIT_INSTANT_TS).execute();
  }

  public Iterator<List<WriteStatus>> handleUpdate(String instantTime, String partitionPath, String fileId,
                                                  Map<String, HoodieRecord<T>> keyToNewRecords, HoodieBaseFile oldDataFile) throws IOException {
    // these are updates
    HoodieSparkMergeHandle upsertHandle = getUpdateHandle(instantTime, partitionPath, fileId, keyToNewRecords, oldDataFile);
    return handleUpdateInternal(upsertHandle, instantTime, fileId);
  }

  protected Iterator<List<WriteStatus>> handleUpdateInternal(HoodieSparkMergeHandle upsertHandle, String instantTime,
                                                             String fileId) throws IOException {
    if (upsertHandle.getOldFilePath() == null) {
      throw new HoodieUpsertException(
          "Error in finding the old file path at commit " + instantTime + " for fileId: " + fileId);
    } else {
      SparkMergeHelper.newInstance().runMerge(this, upsertHandle);
    }


    // TODO(vc): This needs to be revisited
    if (upsertHandle.getWriteStatus().getPartitionPath() == null) {
      LOG.info("Upsert Handle has partition path as null " + upsertHandle.getOldFilePath() + ", "
          + upsertHandle.getWriteStatus());
    }
    return Collections.singletonList(Collections.singletonList(upsertHandle.getWriteStatus())).iterator();
  }

  protected HoodieSparkMergeHandle getUpdateHandle(String instantTime, String partitionPath, String fileId,
                                                   Map<String, HoodieRecord<T>> keyToNewRecords, HoodieBaseFile dataFileToBeMerged) {
    if (requireSortedRecords()) {
      return new HoodieSortedMergeHandle<>(config, instantTime, this, keyToNewRecords, partitionPath, fileId,
          dataFileToBeMerged, (SparkTaskContextSupplier) taskContextSupplier);
    } else {
      return new HoodieSparkMergeHandle<>(config, instantTime, this, keyToNewRecords, partitionPath, fileId,
          dataFileToBeMerged, taskContextSupplier);
    }
  }

  public Iterator<List<WriteStatus>> handleInsert(String instantTime, String partitionPath, String fileId,
                                                  Map<String, HoodieRecord<? extends HoodieRecordPayload>> recordMap) {
    HoodieSparkCreateHandle createHandle =
        new HoodieSparkCreateHandle(config, instantTime, this, partitionPath, fileId, recordMap, taskContextSupplier);
    createHandle.write();
    return Collections.singletonList(Collections.singletonList(createHandle.close())).iterator();
  }

  @Override
  public HoodieCleanMetadata clean(HoodieEngineContext context, String cleanInstantTime) {
    return new SparkCleanActionExecutor((HoodieSparkEngineContext) context, config, this, cleanInstantTime).execute();
  }

  @Override
  public HoodieRollbackMetadata rollback(HoodieEngineContext context, String rollbackInstantTime, HoodieInstant commitInstant, boolean deleteInstants) {
    return new SparkCopyOnWriteRollbackActionExecutor((HoodieSparkEngineContext) context, config, this, rollbackInstantTime, commitInstant, deleteInstants).execute();
  }

  @Override
  public HoodieSavepointMetadata savepoint(HoodieEngineContext context, String instantToSavepoint, String user, String comment) {
    return new SparkSavepointActionExecutor((HoodieSparkEngineContext) context, config, this, instantToSavepoint, user, comment).execute();
  }

  @Override
  public HoodieRestoreMetadata restore(HoodieEngineContext context, String restoreInstantTime, String instantToRestore) {
    return new SparkCopyOnWriteRestoreActionExecutor((HoodieSparkEngineContext) context, config, this, restoreInstantTime, instantToRestore).execute();
  }

}
