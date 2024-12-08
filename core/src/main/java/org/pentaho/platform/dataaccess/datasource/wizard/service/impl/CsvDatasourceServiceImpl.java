/*!
* This program is free software; you can redistribute it and/or modify it under the
* terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
* Foundation.
*
* You should have received a copy of the GNU Lesser General Public License along with this
* program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
* or from the Free Software Foundation, Inc.,
* 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
*
* This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
* without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
* See the GNU Lesser General Public License for more details.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
*/

package org.pentaho.platform.dataaccess.datasource.wizard.service.impl;

import com.thoughtworks.xstream.XStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pentaho.agilebi.modeler.ModelerWorkspace;
import org.pentaho.agilebi.modeler.gwt.GwtModelerWorkspaceHelper;
import org.pentaho.agilebi.modeler.models.JoinFieldModel;
import org.pentaho.agilebi.modeler.models.JoinRelationshipModel;
import org.pentaho.agilebi.modeler.models.JoinTableModel;
import org.pentaho.database.model.DatabaseType;
import org.pentaho.metadata.model.Domain;
import org.pentaho.metadata.model.LogicalModel;
import org.pentaho.metadata.util.SerializationService;
import org.pentaho.platform.api.engine.IAuthorizationPolicy;
import org.pentaho.platform.api.engine.IPentahoSession;
import org.pentaho.platform.dataaccess.datasource.beans.BogoPojo;
import org.pentaho.platform.dataaccess.datasource.wizard.csv.CsvUtils;
import org.pentaho.platform.dataaccess.datasource.wizard.csv.FileUtils;
import org.pentaho.platform.dataaccess.datasource.wizard.models.CsvFileInfo;
import org.pentaho.platform.dataaccess.datasource.wizard.models.CsvTransformGeneratorException;
import org.pentaho.platform.dataaccess.datasource.wizard.models.DatasourceDTO;
import org.pentaho.platform.dataaccess.datasource.wizard.models.FileInfo;
import org.pentaho.platform.dataaccess.datasource.wizard.models.ModelInfo;
import org.pentaho.platform.dataaccess.datasource.wizard.service.DatasourceServiceException;
import org.pentaho.platform.dataaccess.datasource.wizard.service.agile.AgileHelper;
import org.pentaho.platform.dataaccess.datasource.wizard.service.agile.CsvTransformGenerator;
import org.pentaho.platform.dataaccess.datasource.wizard.service.gwt.ICsvDatasourceService;
import org.pentaho.platform.dataaccess.datasource.wizard.sources.csv.FileTransformStats;
import org.pentaho.platform.dataaccess.metadata.messages.Messages;
import org.pentaho.platform.engine.core.system.PentahoBase;
import org.pentaho.platform.engine.core.system.PentahoSessionHolder;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.plugin.action.kettle.KettleSystemListener;
import org.pentaho.reporting.libraries.base.util.StringUtils;
import org.pentaho.platform.dataaccess.datasource.wizard.models.Aggregation;
import org.pentaho.platform.dataaccess.datasource.wizard.models.ColumnInfo;
import org.pentaho.platform.dataaccess.datasource.wizard.models.ColumnInfoCollection;
import org.pentaho.platform.dataaccess.datasource.wizard.models.DataRow;
import org.pentaho.platform.dataaccess.datasource.wizard.models.DatasourceModel;
import org.pentaho.platform.dataaccess.datasource.wizard.models.GuiStateModel;
import org.pentaho.platform.dataaccess.datasource.wizard.models.ModelInfoValidationListenerCollection;
import org.pentaho.platform.dataaccess.datasource.wizard.models.RelationalModelValidationListenerCollection;
import org.pentaho.platform.dataaccess.datasource.wizard.models.WizardModel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings( "unchecked" )
public class CsvDatasourceServiceImpl extends PentahoBase implements ICsvDatasourceService {
  public static final byte[] lock = new byte[ 0 ];

  private static final long serialVersionUID = 2498165533158485182L;

  private Log logger = LogFactory.getLog( CsvDatasourceServiceImpl.class );

  private ModelerService modelerService = new ModelerService();
  private DSWDatasourceServiceImpl datasourceService = new DSWDatasourceServiceImpl();

  private ModelerWorkspace modelerWorkspace;

  public CsvDatasourceServiceImpl() {
    super();
    modelerWorkspace = new ModelerWorkspace( new GwtModelerWorkspaceHelper() );

    try {
      modelerWorkspace.setGeoContext( datasourceService.getGeoContext() );
    } catch ( DatasourceServiceException e ) {
      logger.warn( "Could not get a GeoContext, auto-modeling will not use be able to auto detect geographies", e );
    }


    modelerService = new ModelerService();
  }

  public Log getLogger() {
    return logger;
  }

  public String getEncoding( String fileName ) {
    checkPermissions();
    String encoding = null;
    try {
      CsvUtils csvModelService = new CsvUtils();
      encoding = csvModelService.getEncoding( fileName );
    } catch ( Exception e ) {
      logger.error( e );
    }
    return encoding;
  }

  public ModelInfo stageFile( String fileName, String delimiter, String enclosure, boolean isFirstRowHeader,
                              String encoding )
    throws Exception {
    checkPermissions();
    ModelInfo modelInfo;
    fileName = FilenameUtils.getName( fileName );
    try {
      int headerRows = isFirstRowHeader ? 1 : 0;
      modelInfo = new CsvUtils().generateFields( "", fileName, AgileHelper.getCsvSampleRowSize(),
        delimiter, enclosure, headerRows, true, true, encoding ); //$NON-NLS-1$
    } catch ( FileNotFoundException e ) {
      logger.error( e );
      throw new Exception( "File was not found: " + fileName );
    } catch ( Exception e ) {
      logger.error( e );
      throw e;
    }
    return modelInfo;
  }

  public FileInfo[] getStagedFiles() throws Exception {
    checkPermissions();
    FileInfo[] files;
    try {
      FileUtils fileService = new FileUtils();
      files = fileService.listFiles();
    } catch ( Exception e ) {
      logger.error( e );
      throw e;
    }
    return files;
  }

  public FileTransformStats generateDomain( DatasourceDTO datasourceDto ) throws Exception {
    checkPermissions();
    synchronized ( lock ) {
      ModelInfo modelInfo = datasourceDto.getCsvModelInfo();
      IPentahoSession pentahoSession = null;
      try {
        pentahoSession = PentahoSessionHolder.getSession();
        KettleSystemListener.environmentInit( pentahoSession );

        String statsKey =
          FileTransformStats.class.getSimpleName() + "_" + modelInfo.getFileInfo().getTmpFilename(); //$NON-NLS-1$

        FileTransformStats stats = new FileTransformStats();
        pentahoSession.setAttribute( statsKey, stats );
        CsvTransformGenerator csvTransformGenerator =
          new CsvTransformGenerator( modelInfo, AgileHelper.getDatabaseMeta() );
        csvTransformGenerator.setTransformStats( stats );


        try {
          csvTransformGenerator.dropTable( modelInfo.getStageTableName() );
        } catch ( CsvTransformGeneratorException e ) {
          // this is ok, the table may not have existed.
          logger.info( "Could not drop table before staging" ); //$NON-NLS-1$
        }
        csvTransformGenerator.createOrModifyTable( pentahoSession );

        // no longer need to truncate the table since we dropped it a few lines up, so just pass false
        csvTransformGenerator.loadTable( false, pentahoSession, true );

        ArrayList<String> combinedErrors = new ArrayList<String>( modelInfo.getCsvInputErrors() );
        combinedErrors.addAll( modelInfo.getTableOutputErrors() );
        if ( stats.getErrors() != null && stats.getErrors().size() > 0 ) {
          stats.getErrors().addAll( combinedErrors );
        } else {
          stats.setErrors( combinedErrors );
        }

        // wait until it it done
        while ( !stats.isRowsFinished() ) {
          Thread.sleep( 200 );
        }

        modelerWorkspace.setDomain( modelerService.generateCSVDomain( modelInfo ) );
        modelerWorkspace.getWorkspaceHelper().autoModelFlat( modelerWorkspace );
        modelerWorkspace.getWorkspaceHelper().autoModelRelationalFlat( modelerWorkspace );
        modelerWorkspace.setModelName( modelInfo.getDatasourceName() );
        modelerWorkspace.getWorkspaceHelper().populateDomain( modelerWorkspace );
        Domain workspaceDomain = modelerWorkspace.getDomain();

        XStream xstream = SerializationService.createXStreamWithAllowedTypes(null, null);
        String serializedDto = xstream.toXML( datasourceDto );
        workspaceDomain.getLogicalModels().get( 0 ).setProperty( "datasourceModel", serializedDto );
        workspaceDomain.getLogicalModels().get( 0 ).setProperty( "DatasourceType", "CSV" );
        prepareForSerialization( workspaceDomain );

        modelerService.serializeModels( workspaceDomain, modelerWorkspace.getModelName() );
        stats.setDomain( modelerWorkspace.getDomain() );

        return stats;
      } catch ( Exception e ) {
        logger.error( e.getMessage() );
        throw e;
      } finally {
        if ( pentahoSession != null ) {
          pentahoSession.destroy();
        }
      }
    }
  }

  protected void prepareForSerialization( Domain domain ) throws IOException {

    /*
     * This method is responsible for cleaning up legacy information when
     * changing datasource types and also manages CSV files for CSV based
     * datasources.
     */

    String relativePath = PentahoSystem.getSystemSetting( "file-upload-defaults/relative-path",
      String.valueOf( FileUtils.DEFAULT_RELATIVE_UPLOAD_FILE_PATH ) ); //$NON-NLS-1$
    String path = PentahoSystem.getApplicationContext().getSolutionPath( relativePath );
    String TMP_FILE_PATH =
      File.separatorChar + "system" + File.separatorChar + File.separatorChar + "tmp" + File.separatorChar;
    String sysTmpDir = PentahoSystem.getApplicationContext().getSolutionPath( TMP_FILE_PATH );
    LogicalModel logicalModel = domain.getLogicalModels().get( 0 );
    String modelState = (String) logicalModel.getProperty( "datasourceModel" ); //$NON-NLS-1$

    if ( modelState != null ) {

      XStream xs = SerializationService.createXStreamWithAllowedTypes( null, DatasourceDTO.class, DatasourceModel.class,
        Aggregation.class,
        ColumnInfo.class, ColumnInfoCollection.class, CsvFileInfo.class, DataRow.class, FileInfo.class,
        GuiStateModel.class,
        ModelInfo.class, ModelInfoValidationListenerCollection.class, RelationalModelValidationListenerCollection.class,
        WizardModel.class, DatabaseType.class, Domain.class, LogicalModel.class, JoinFieldModel.class, JoinRelationshipModel.class, JoinTableModel.class );
      DatasourceDTO datasource = (DatasourceDTO) xs.fromXML( modelState );
      CsvFileInfo csvFileInfo = datasource.getCsvModelInfo().getFileInfo();
      String tmpFileName = csvFileInfo.getTmpFilename();
      String csvFileName = csvFileInfo.getFilename();
      File tmpFile = new File( sysTmpDir + File.separatorChar + tmpFileName );

      // Move CSV temporary file to final destination.
      if ( tmpFile.exists() ) {
        File csvFile = new File( path + File.separatorChar + csvFileName );
        org.apache.commons.io.FileUtils.copyFile( tmpFile, csvFile );
      }

      // Cleanup logic when updating from SQL datasource to CSV
      // datasource.
      datasource.setQuery( null );
      // Update datasourceModel with the new modelState
      modelState = xs.toXML( datasource );
      logicalModel.setProperty( "datasourceModel", modelState );
    }
  }

  public List<String> getPreviewRows( String filename, boolean isFirstRowHeader, int rows, String encoding )
    throws Exception {
    checkPermissions();
    List<String> previewRows = null;
    if ( !StringUtils.isEmpty( filename ) ) {
      CsvUtils service = new CsvUtils();
      ModelInfo mi = service.getFileContents( "", filename, ",", "\"", rows, isFirstRowHeader,
        encoding ); //$NON-NLS-1$  //$NON-NLS-2$  //$NON-NLS-3$
      previewRows = mi.getFileInfo().getContents();
    }
    return previewRows;
  }

  /**
   * Returns true if the current user has Manage Data Source Security. Otherwise returns false.
   * @return
   */
  protected boolean hasManageDataAccessPermission() {
    // If this breaks an OEM's plugin, provide a get-out-of-jail card with an entry in the pentaho.xml.
    final String override = PentahoSystem.getSystemSetting( "data-access-override", "false" );
    final Boolean rtnOverride = Boolean.valueOf( override );
    if ( !rtnOverride ) {
      final IAuthorizationPolicy policy = PentahoSystem.get( IAuthorizationPolicy.class );
      if ( policy != null ) {
        return policy.isAllowed( "org.pentaho.platform.dataaccess.datasource.security.manage" );
      } else {
        return false;
      }
    } else {
      return true; // Override the security policy with the entry in the pentaho.xml.
    }
  }

  private void checkPermissions() throws SecurityException {
    if ( !hasManageDataAccessPermission() ) {
      throw new SecurityException( Messages.getErrorString( "CsvDatasourceServiceImpl.ERROR_0009_UNAUTHORIZED" ) );
    }
  }

  @Override
  public BogoPojo gwtWorkaround( BogoPojo pojo ) {
    return pojo;
  }

}
