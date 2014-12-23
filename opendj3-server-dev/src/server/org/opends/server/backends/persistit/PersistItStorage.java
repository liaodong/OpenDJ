/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.backends.persistit;

import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.admin.std.server.PersistitBackendCfg;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableStorage;

import com.persistit.Exchange;
import com.persistit.Key;
import com.persistit.Persistit;
import com.persistit.Transaction;
import com.persistit.Tree;
import com.persistit.TreeBuilder;
import com.persistit.Value;
import com.persistit.Volume;
import com.persistit.exception.PersistitException;
import com.persistit.exception.RollbackException;

@SuppressWarnings("javadoc")
public final class PersistItStorage implements Storage
{
  private final class CursorImpl implements Cursor
  {
    private ByteString currentKey;
    private ByteString currentValue;
    private final Exchange ex;

    private CursorImpl(final Exchange exchange)
    {
      this.ex = exchange;
    }

    @Override
    public void close()
    {
      // Release immediately because this exchange did not come from the txn cache
      db.releaseExchange(ex);
    }

    @Override
    public ByteString getKey()
    {
      if (currentKey == null)
      {
        currentKey = keyToBytes(ex.getKey());
      }
      return currentKey;
    }

    @Override
    public ByteString getValue()
    {
      if (currentValue == null)
      {
        currentValue = valueToBytes(ex.getValue());
      }
      return currentValue;
    }

    @Override
    public boolean next()
    {
      clearCurrentKeyAndValue();
      try
      {
        return ex.next();
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean positionToKey(final ByteSequence key)
    {
      clearCurrentKeyAndValue();
      bytesToKey(ex.getKey(), key);
      try
      {
        ex.fetch();
        return ex.getValue().isDefined();
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean positionToKeyOrNext(final ByteSequence key)
    {
      clearCurrentKeyAndValue();
      bytesToKey(ex.getKey(), key);
      try
      {
        ex.fetch();
        return ex.getValue().isDefined() || ex.next();
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean positionToLastKey()
    {
      try
      {
        clearCurrentKeyAndValue();
        ex.getKey().to(Key.AFTER);
        return ex.previous();
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean previous()
    {
      clearCurrentKeyAndValue();
      try
      {
        return ex.previous();
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    private void clearCurrentKeyAndValue()
    {
      currentKey = null;
      currentValue = null;
    }
  }

  private final class ImporterImpl implements Importer
  {
    private final TreeBuilder importer = new TreeBuilder(db);
    private final Key importKey = new Key(db);
    private final Value importValue = new Value(db);
    private final Map<TreeName, Tree> trees = new HashMap<TreeName, Tree>();

    @Override
    public void close()
    {
      try
      {
        importer.merge();
      }
      catch (final Exception e)
      {
        throw new StorageRuntimeException(e);
      }
      finally
      {
        PersistItStorage.this.close();
      }
    }

    @Override
    public void createTree(final TreeName treeName)
    {
      try
      {
        final Tree tree = volume.getTree(treeName.toString(), true);
        trees.put(treeName, tree);
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public void put(final TreeName treeName, final ByteSequence key,
        final ByteSequence value)
    {
      try
      {
        final Tree tree = trees.get(treeName);
        importer.store(tree, bytesToKey(importKey, key),
            bytesToValue(importValue, value));
      }
      catch (final Exception e)
      {
        throw new StorageRuntimeException(e);
      }
    }
  }

  private final class StorageImpl implements WriteableStorage
  {
    private final Map<TreeName, Exchange> exchanges = new HashMap<TreeName, Exchange>();

    @Override
    public void create(final TreeName treeName, final ByteSequence key,
        final ByteSequence value)
    {
      try
      {
        final Exchange ex = getExchange(treeName);
        bytesToKey(ex.getKey(), key);
        bytesToValue(ex.getValue(), value);
        ex.store();
      }
      catch (final Exception e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public void delete(final TreeName treeName, final ByteSequence key)
    {
      try
      {
        final Exchange ex = getExchange(treeName);
        bytesToKey(ex.getKey(), key);
        ex.remove();
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public void deleteTree(final TreeName treeName)
    {
      Exchange ex = null;
      try
      {
        ex = getExchange(treeName);
        ex.removeTree();
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
      finally
      {
        db.releaseExchange(ex);
      }
    }

    @Override
    public ByteString getRMW(final TreeName treeName, final ByteSequence key)
    {
      return read(treeName, key);
    }

    @Override
    public Cursor openCursor(final TreeName treeName)
    {
      try
      {
        /*
         * Acquire a new exchange for the cursor rather than using a cached
         * exchange in order to avoid reentrant accesses to the same tree
         * interfering with the cursor position.
         */
        return new CursorImpl(getExchange0(treeName, false));
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public void openTree(final TreeName treeName)
    {
      Exchange ex = null;
      try
      {
        ex = getExchange0(treeName, true);
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
      finally
      {
        db.releaseExchange(ex);
      }
    }

    @Override
    public boolean putIfAbsent(final TreeName treeName, final ByteSequence key,
        final ByteSequence value)
    {
      try
      {
        // There is no CAS (Compare And Swap) operation to do here :)
        // Following code is fine because Persistit provides snapshot isolation.
        // If another thread tries to update the same key, we'll get a RollbackException
        // And the write operation will be retried (see write() method in this class)
        final Exchange ex = getExchange(treeName);
        bytesToKey(ex.getKey(), key);
        ex.fetch();
        final Value exValue = ex.getValue();
        if (exValue.isDefined())
        {
          return false;
        }
        bytesToValue(exValue, value);
        ex.store();
        return true;
      }
      catch (final Exception e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public ByteString read(final TreeName treeName, final ByteSequence key)
    {
      try
      {
        final Exchange ex = getExchange(treeName);
        bytesToKey(ex.getKey(), key);
        ex.fetch();
        return valueToBytes(ex.getValue());
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean remove(final TreeName treeName, final ByteSequence key)
    {
      try
      {
        final Exchange ex = getExchange(treeName);
        bytesToKey(ex.getKey(), key);
        return ex.remove();
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public void renameTree(final TreeName oldTreeName,
        final TreeName newTreeName)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public void truncateTree(final TreeName treeName)
    {
      try
      {
        getExchange(treeName).removeAll();
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public void update(final TreeName treeName, final ByteSequence key,
        final UpdateFunction f)
    {
      try
      {
        final Exchange ex = getExchange(treeName);
        bytesToKey(ex.getKey(), key);
        ex.fetch();
        final ByteSequence oldValue = valueToBytes(ex.getValue());
        final ByteSequence newValue = f.computeNewValue(oldValue);
        ex.getValue().clear().putByteArray(newValue.toByteArray());
        ex.store();
      }
      catch (final Exception e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    private Exchange getExchange(final TreeName treeName)
        throws PersistitException
    {
      Exchange exchange = exchanges.get(treeName);
      if (exchange == null)
      {
        exchange = getExchange0(treeName, false);
        exchanges.put(treeName, exchange);
      }
      return exchange;
    }

    private void release()
    {
      for (final Exchange ex : exchanges.values())
      {
        db.releaseExchange(ex);
      }
    }
  }

  private static void clearAndCreateDbDir(final File dbDir)
  {
    if (dbDir.exists())
    {
      for (final File child : dbDir.listFiles())
      {
        child.delete();
      }
    }
    else
    {
      dbDir.mkdirs();
    }
  }

  private File backendDirectory;
  private PersistitBackendCfg config;
  private Persistit db;
  private Properties properties;
  private Volume volume;

  @Override
  public void close()
  {
    if (db != null)
    {
      try
      {
        db.close();
        db = null;
      }
      catch (final PersistitException e)
      {
        throw new IllegalStateException(e);
      }
    }
  }

  @Override
  public void closeTree(final TreeName treeName)
  {
    // nothing to do, in persistit you close the volume itself
  }

  @Override
  public void initialize(final PersistitBackendCfg cfg)
  {
    this.config = cfg;
    this.backendDirectory = new File(getFileForPath(config.getDBDirectory()),
        config.getBackendId());

    properties = new Properties();
    properties.setProperty("datapath", backendDirectory.toString());
    properties.setProperty("logpath", backendDirectory.toString());
    properties.setProperty("logfile", "${logpath}/dj_${timestamp}.log");
    properties.setProperty("buffer.count.16384", "64K");
    properties.setProperty("journalpath", "${datapath}/dj_journal");
    properties.setProperty("volume.1", "${datapath}/dj"
        + ",create,pageSize:16K" + ",initialSize:50M" + ",extensionSize:1M"
        + ",maximumSize:10G");
  }

  @Override
  public boolean isValid()
  {
    return !db.isFatal();
  }

  @Override
  public void open()
  {
    try
    {
      db = new Persistit(properties);
      db.initialize();
      volume = db.loadVolume("dj");
    }
    catch (final PersistitException e)
    {
      throw new StorageRuntimeException(e);
    }
  }

  @Override
  public <T> T read(final ReadOperation<T> operation) throws Exception
  {
    final Transaction txn = db.getTransaction();
    for (;;)
    {
      txn.begin();
      try
      {
        final StorageImpl storageImpl = new StorageImpl();
        try
        {
          final T result = operation.run(storageImpl);
          txn.commit();
          return result;
        }
        catch (final StorageRuntimeException e)
        {
          throw (Exception) e.getCause();
        }
        finally
        {
          storageImpl.release();
        }
      }
      catch (final RollbackException e)
      {
        // retry
      }
      catch (final Exception e)
      {
        txn.rollback();
        throw e;
      }
      finally
      {
        txn.end();
      }
    }
  }

  @Override
  public Importer startImport()
  {
    clearAndCreateDbDir(backendDirectory);
    open();
    return new ImporterImpl();
  }

  /**
   * Replace persistit reserved comma character with an underscore character.
   */
  public String toSuffixName(final String prefix)
  {
    return prefix.replaceAll("[,=]", "_");
  }

  @Override
  public void write(final WriteOperation operation) throws Exception
  {
    final Transaction txn = db.getTransaction();
    for (;;)
    {
      txn.begin();
      try
      {
        final StorageImpl storageImpl = new StorageImpl();
        try
        {
          operation.run(storageImpl);
          txn.commit();
          return;
        }
        catch (final StorageRuntimeException e)
        {
          throw (Exception) e.getCause();
        }
        finally
        {
          storageImpl.release();
        }
      }
      catch (final RollbackException e)
      {
        // retry
      }
      catch (final Exception e)
      {
        txn.rollback();
        throw e;
      }
      finally
      {
        txn.end();
      }
    }
  }

  /*
   * TODO: it would be nice to use the low-level key/value APIs. They seem quite
   * inefficient at the moment for simple byte arrays.
   */
  private Key bytesToKey(final Key key, final ByteSequence bytes)
  {
    final byte[] tmp = bytes.toByteArray();
    return key.clear().appendByteArray(tmp, 0, tmp.length);
  }

  private Value bytesToValue(final Value value, final ByteSequence bytes)
  {
    value.clear().putByteArray(bytes.toByteArray());
    return value;
  }

  private Exchange getExchange0(final TreeName treeName, final boolean create)
      throws PersistitException
  {
    return db.getExchange(volume, treeName.toString(), create);
  }

  private ByteString keyToBytes(final Key key)
  {
    return ByteString.wrap(key.reset().decodeByteArray());
  }

  private ByteString valueToBytes(final Value value)
  {
    if (value.isDefined())
    {
      return ByteString.wrap(value.getByteArray());
    }
    return null;
  }
}