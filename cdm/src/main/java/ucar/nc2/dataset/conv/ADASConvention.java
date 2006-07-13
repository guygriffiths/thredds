// $Id:ADASConvention.java 51 2006-07-12 17:13:13Z caron $
/*
 * Copyright 1997-2006 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package ucar.nc2.dataset.conv;

import ucar.ma2.*;
import ucar.nc2.*;
import ucar.nc2.units.SimpleUnit;
import ucar.nc2.util.CancelTask;
import ucar.nc2.dataset.*;
import ucar.unidata.util.StringUtil;
import ucar.unidata.util.Parameter;

import ucar.unidata.geoloc.*;
import ucar.unidata.geoloc.projection.*;

import java.io.IOException;
import java.util.*;

/**
 * ADAS netcdf files.
 *
 * @author caron
 * @version $Revision:51 $ $Date:2006-07-12 17:13:13Z $
 */

public class ADASConvention extends CoordSysBuilder {

  static private java.text.SimpleDateFormat dateFormat;
  static {
    dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
    dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
  }

  // private double originX = 0.0, originY = 0.0;
  private ProjectionCT projCT = null;
  private boolean debugProj = false;

  /** create a NetcdfDataset out of this NetcdfFile, adding coordinates etc. */
  public void augmentDataset( NetcdfDataset ds, CancelTask cancelTask) throws IOException {
    this.conventionName = "ARPS/ADAS";

    // old way
    Attribute att = ds.findGlobalAttribute("MAPPROJ");
    int projType = att.getNumericValue().intValue();

    double lat1 = findAttributeDouble( ds, "TRUELAT1");
    double lat2 = findAttributeDouble( ds, "TRUELAT2");
    double lat_origin = lat1;
    double lon_origin = findAttributeDouble( ds, "TRUELON");
    double false_easting = 0.0;
    double false_northing = 0.0;

    // new way
    String projName = ds.findAttValueIgnoreCase(null, "grid_mapping_name", null);
    if (projName != null) {
      projName = projName.trim();
      lat_origin = findAttributeDouble( ds, "latitude_of_projection_origin");
      lon_origin = findAttributeDouble( ds, "longitude_of_central_meridian");
      false_easting = findAttributeDouble( ds, "false_easting");
      false_northing = findAttributeDouble( ds, "false_northing");

      Attribute att2 = ds.findGlobalAttributeIgnoreCase("standard_parallel");
      if (att2 != null) {
        lat1 = att2.getNumericValue().doubleValue();
        lat2 = (att2.getLength() > 1) ? att2.getNumericValue(1).doubleValue() : lat1;
      }
    } else {
      if (projType == 2) projName = "lambert_conformal_conic";
    }

    Variable coord_var = ds.findVariable("x_stag");
    String units = ds.findAttValueIgnoreCase(coord_var, "units", null);

    ProjectionImpl proj = null;
    if (projName.equalsIgnoreCase("lambert_conformal_conic")) {
      proj = new LambertConformal(lat_origin, lon_origin, lat1, lat2, false_easting, false_northing, units);
      projCT = new ProjectionCT("Projection", "FGDC", proj);
      if (false_easting == 0.0) calcCenterPoints( ds, proj); // old way
    } else {
        parseInfo.append("ERROR: unknown projection type = "+projName);
    }

    if (debugProj) {
      System.out.println(" using LC "+proj.paramsToString());
      double lat_check = findAttributeDouble( ds, "CTRLAT");
      double lon_check = findAttributeDouble( ds, "CTRLON");

      LatLonPointImpl lpt0 = new LatLonPointImpl( lat_check,  lon_check);
      ProjectionPoint ppt0 = proj.latLonToProj(lpt0, new ProjectionPointImpl());
      System.out.println("CTR lpt0= "+lpt0+" ppt0="+ppt0);

      Variable xstag = ds.findVariable("x_stag");
      ArrayFloat.D1 xstagData = (ArrayFloat.D1) xstag.read();
      float center_x = xstagData.get( (int) xstag.getSize() - 1);
      Variable ystag = ds.findVariable("y_stag");
      ArrayFloat.D1 ystagData = (ArrayFloat.D1) ystag.read();
      float center_y = ystagData.get( (int) ystag.getSize() - 1);
      System.out.println("CTR should be x,y= "+center_x/2000+", "+center_y/2000);

      lpt0 = new LatLonPointImpl( lat_origin,  lon_origin);
      ppt0 = proj.latLonToProj(lpt0, new ProjectionPointImpl());
      System.out.println("ORIGIN lpt0= "+lpt0+" ppt0="+ppt0);

      lpt0 = new LatLonPointImpl( lat_origin,  lon_origin);
      ppt0 = proj.latLonToProj(lpt0, new ProjectionPointImpl());
      System.out.println("TRUE ORIGIN lpt0= "+lpt0+" ppt0="+ppt0);
    }

    if (projCT != null) {
        VariableDS v = makeCoordinateTransformVariable(ds, projCT);
        v.addAttribute( new Attribute("_CoordinateAxisTypes", "GeoX GeoY"));
        ds.addVariable(null, v);
    }

    if (ds.findVariable("x_stag") != null)
      ds.addCoordinateAxis( makeCoordAxis( ds, "x"));
    if (ds.findVariable("y_stag") != null)
      ds.addCoordinateAxis( makeCoordAxis( ds, "y"));
    if (ds.findVariable("z_stag") != null)
      ds.addCoordinateAxis( makeCoordAxis( ds, "z"));

    Variable zsoil = ds.findVariable("ZPSOIL");
    if (zsoil != null)
      zsoil.addAttribute( new Attribute("_CoordinateAxisType", AxisType.GeoZ.toString()));

    ds.finish();
  }

  // old
  private void calcCenterPoints(NetcdfDataset ds, Projection proj) throws IOException {
    double lat_check = findAttributeDouble( ds, "CTRLAT");
    double lon_check = findAttributeDouble( ds, "CTRLON");

    LatLonPointImpl lpt0 = new LatLonPointImpl( lat_check,  lon_check);
    ProjectionPoint ppt0 = proj.latLonToProj(lpt0, new ProjectionPointImpl());
    System.out.println("CTR lpt0= "+lpt0+" ppt0="+ppt0);

    Variable xstag = ds.findVariable("x_stag");
    int nxpts = (int) xstag.getSize();
    ArrayFloat.D1 xstagData = (ArrayFloat.D1) xstag.read();
    float center_x = xstagData.get( nxpts - 1);
    double false_easting= center_x/2000 - ppt0.getX()*1000.0;
    System.out.println("false_easting= "+false_easting);

    Variable ystag = ds.findVariable("y_stag");
    int nypts = (int) ystag.getSize();
    ArrayFloat.D1 ystagData = (ArrayFloat.D1) ystag.read();
    float center_y = ystagData.get( nypts - 1);
    double false_northing= center_y/2000 - ppt0.getY()*1000.0;
    System.out.println("false_northing= "+false_northing);

    double dx = findAttributeDouble( ds, "DX");
    double dy = findAttributeDouble( ds, "DY");

    double w = dx * (nxpts-1);
    double h = dy * (nypts -1);
    double startx = ppt0.getX()*1000.0 - w/2;
    double starty = ppt0.getY()*1000.0 - h/2;

    ds.setValues( xstag, nxpts, startx, dx);
    ds.setValues( ystag, nypts, starty, dy);
  }

   /////////////////////////////////////////////////////////////////////////

  protected void makeCoordinateTransforms( NetcdfDataset ds) {
    if (projCT != null) {
      VarProcess vp = findVarProcess(projCT.getName());
      vp.isCoordinateTransform = true;
      vp.ct = projCT;
    }
    super.makeCoordinateTransforms( ds);
  }

  protected AxisType getAxisType( NetcdfDataset ds, VariableEnhanced ve) {
    Variable v = (Variable) ve;
    String vname = v.getName();

   if (vname.equalsIgnoreCase("x") || vname.equalsIgnoreCase("x_stag"))
      return AxisType.GeoX;

    if (vname.equalsIgnoreCase("lon"))
      return AxisType.Lon;

    if (vname.equalsIgnoreCase("y") || vname.equalsIgnoreCase("y_stag"))
      return AxisType.GeoY;

    if (vname.equalsIgnoreCase("lat"))
      return AxisType.Lat;

    if (vname.equalsIgnoreCase("z") || vname.equalsIgnoreCase("z_stag"))
      return AxisType.GeoZ;

    if (vname.equalsIgnoreCase("Z"))
       return AxisType.Height;

    if (vname.equalsIgnoreCase("time"))
      return AxisType.Time;

    String unit = ve.getUnitsString();
    if (unit != null) {
      if ( SimpleUnit.isCompatible("millibar", unit))
        return AxisType.Pressure;

      if ( SimpleUnit.isCompatible("m", unit))
        return AxisType.Height;
    }
    

    return null;
  }

  /**  Does increasing values of Z go vertical  up?
    * @return "up" if this is a Vertical (z) coordinate axis which goes up as coords get bigger,
    * else return "down" */
  public String getZisPositive( CoordinateAxis v) {
  	return "down"; //eta coords decrease upward
  }

  //////////////////////////////////////////////////////////////////////////////////////////////

  private CoordinateAxis makeCoordAxis( NetcdfDataset ds, String axisName) throws IOException {
    Variable stagV = ds.findVariable(axisName+"_stag");
    Array data_stag = stagV.read();
    int n = (int) data_stag.getSize() - 1;
    Array data = Array.factory( data_stag.getElementType(), new int[] { n} );
    Index stagIndex = data_stag.getIndex();
    Index dataIndex = data.getIndex();
    for (int i=0; i<n; i++) {
      double val = data_stag.getDouble( stagIndex.set(i)) + data_stag.getDouble( stagIndex.set(i+1));
      data.setDouble( dataIndex.set(i), 0.5 * val);
    }

    String units = ds.findAttValueIgnoreCase( stagV, "units", "m");
    CoordinateAxis v = new CoordinateAxis1D( ds, null, axisName, DataType.DOUBLE, axisName, units, "synthesized non-staggered "+axisName+" coordinate");
    v.setCachedData( data, true);
    return v;
  }

  private double findAttributeDouble( NetcdfDataset ds, String attname) {
    Attribute att = ds.findGlobalAttributeIgnoreCase(attname);
    if (att == null) return Double.NaN;
    return att.getNumericValue().doubleValue();
  }

  /* private VerticalCT makeWRFEtaVerticalCoordinateTransform(NetcdfDataset ds, CoordinateSystem cs) {
    if ((null == ds.findVariable("PH")) || (null == ds.findVariable("PHB")) ||
        (null == ds.findVariable("P")) || (null == ds.findVariable("PB")))
      return null;

  	VerticalCT.Type type = VerticalCT.Type.WRFEta;
  	VerticalCT ct = new VerticalCT(type.toString(), conventionName, type);

  	ct.addParameter(new Parameter("height formula", "height(x,y,z) = (PH(x,y,z) + PHB(x,y,z)) / 9.81"));
  	ct.addParameter(new Parameter("perturbation geopotential variable name", "PH"));
  	ct.addParameter(new Parameter("base state geopotential variable name", "PHB"));
  	ct.addParameter(new Parameter("pressure formula", "pressure(x,y,z) = P(x,y,z) + PB(x,y,z)"));
  	ct.addParameter(new Parameter("perturbation pressure variable name", "P"));
  	ct.addParameter(new Parameter("base state pressure variable name", "PB"));
  	ct.addParameter(new Parameter("staggered x", ""+isStaggered(cs.getXaxis())));
  	ct.addParameter(new Parameter("staggered y", ""+isStaggered(cs.getYaxis())));
  	ct.addParameter(new Parameter("staggered z", ""+isStaggered(cs.getZaxis())));
  	ct.addParameter(new Parameter("eta", ""+cs.getZaxis().getName()));

    parseInfo.append(" added vertical coordinate transform = "+type+"\n");
  	return ct;
  }

  private boolean isStaggered(CoordinateAxis axis) {
  	if (axis == null) return false;
  	String name = axis.getName();
  	if (name == null) return false;
  	if (name.endsWith("stag")) return true;
  	return false;
  } */

  /**
   * Assign CoordinateTransform objects to Coordinate Systems.
   */
  protected void assignCoordinateTransforms(NetcdfDataset ncDataset) {
    super.assignCoordinateTransforms(ncDataset);

    // any cs whose got a vertical coordinate with no units
    List csys = ncDataset.getCoordinateSystems();
    for (int i = 0; i < csys.size(); i++) {
      CoordinateSystem cs = (CoordinateSystem) csys.get(i);
      if (cs.getZaxis() != null) {
        String units = cs.getZaxis().getUnitsString();
        if ((units == null) || (units.trim().length() == 0)) {
          VerticalCT vct = makeWRFEtaVerticalCoordinateTransform(ncDataset, cs);
          if (vct != null) {
            cs.addCoordinateTransform(vct);
            parseInfo.append("***Added WRFEta verticalCoordinateTransform to " + cs.getName() + "\n");
          }
        }
      }
    }
  }

  private VerticalCT makeWRFEtaVerticalCoordinateTransform(NetcdfDataset ds, CoordinateSystem cs) {
    if ((null == ds.findVariable("PH")) || (null == ds.findVariable("PHB")) ||
        (null == ds.findVariable("P")) || (null == ds.findVariable("PB")))
      return null;

    WRFEtaTransformBuilder builder = new WRFEtaTransformBuilder(cs);
    return (VerticalCT) builder.makeCoordinateTransform(ds, null);
  }

}