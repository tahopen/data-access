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

package org.pentaho.platform.dataaccess.datasource.wizard.models;

import org.pentaho.platform.dataaccess.datasource.wizard.IWizardDatasource;
import org.pentaho.ui.xul.XulEventSource;
import org.pentaho.ui.xul.stereotype.Bindable;

import java.util.Set;

/**
 * User: nbaker Date: 3/30/11
 */
public interface IWizardModel extends XulEventSource {
  @Bindable String getDatasourceName();

  @Bindable void setDatasourceName( String datasourceName );

  Set<IWizardDatasource> getDatasources();

  void addDatasource( IWizardDatasource datasource );

  void removeDatasourceByType( Class<? extends IWizardDatasource> datasource );

  boolean isEditing();

  void setEditing( boolean editing );

  void setSelectedDatasource( IWizardDatasource datasource );

  IWizardDatasource getSelectedDatasource();

  void reset();

  boolean isReportingOnlyValid();

  void setReportingOnlyValid( boolean valid );
}
