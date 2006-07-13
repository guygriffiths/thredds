// $Id:VAtmSigma.java 51 2006-07-12 17:13:13Z caron $
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

package ucar.nc2.dataset.transform;

import ucar.nc2.dataset.*;
import ucar.nc2.Variable;
import ucar.nc2.Dimension;
import ucar.unidata.geoloc.vertical.AtmosSigma;
import ucar.unidata.util.Parameter;

import java.util.StringTokenizer;

/**
 * Create a atmosphere_sigma_coordinate Vertical Transform from the information in the Coordinate Transform Variable.
 *  *
 * @author caron
 * @version $Revision:51 $ $Date:2006-07-12 17:13:13Z $
 */
public class VAtmSigma extends AbstractCoordTransBuilder {
  private String sigma="", ps="", ptop="";

  public String getTransformName() {
    return "atmosphere_sigma_coordinate";
  }

  public TransformType getTransformType() {
    return TransformType.Vertical;
  }

  public CoordinateTransform makeCoordinateTransform(NetcdfDataset ds, Variable ctv) {
    String formula = getFormula(ds, ctv);
    if (null == formula) return null;

    // parse the formula string
    StringTokenizer stoke = new StringTokenizer(formula);
    while (stoke.hasMoreTokens()) {
      String toke = stoke.nextToken();
      if (toke.equalsIgnoreCase("sigma:"))
        sigma = stoke.nextToken();
      else if (toke.equalsIgnoreCase("ps:"))
        ps = stoke.nextToken();
      else if (toke.equalsIgnoreCase("ptop:"))
        ptop = stoke.nextToken();
    }

    CoordinateTransform rs = new VerticalCT(ctv.getName(), getTransformName(), VerticalCT.Type.Sigma, this);
    rs.addParameter(new Parameter("formula", "pressure(x,y,z) = ptop + sigma(z)*(surfacePressure(x,y)-ptop)"));

    if (!addParameter( rs, AtmosSigma.PS, ds, ps, false)) return null;
    if (!addParameter( rs, AtmosSigma.SIGMA, ds, sigma, false)) return null;
    if (!addParameter( rs, AtmosSigma.PTOP, ds, ptop, true)) return null;

    return rs;
  }

  public String toString() { 
    return "Sigma:" + "sigma:"+sigma + " ps:"+ps + " ptop:"+ptop;
  }


  public ucar.unidata.geoloc.vertical.VerticalTransform makeMathTransform(NetcdfDataset ds, Dimension timeDim, VerticalCT vCT) {
    return new AtmosSigma(ds, timeDim, vCT);
  }
}

