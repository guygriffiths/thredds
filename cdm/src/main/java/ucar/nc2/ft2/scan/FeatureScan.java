/*
 * Copyright 1998-2015 John Caron and University Corporation for Atmospheric Research/Unidata
 *
 *  Portions of this software were developed by the Unidata Program at the
 *  University Corporation for Atmospheric Research.
 *
 *  Access and use of this software shall impose the following obligations
 *  and understandings on the user. The user is granted the right, without
 *  any fee or cost, to use, copy, modify, alter, enhance and distribute
 *  this software, and any derivative works thereof, and its supporting
 *  documentation for any purpose whatsoever, provided that this entire
 *  notice appears in all copies of the software, derivative works and
 *  supporting documentation.  Further, UCAR requests that the user credit
 *  UCAR/Unidata in any publications that result from the use of this
 *  software or in any product that includes this software. The names UCAR
 *  and/or Unidata, however, may not be used in any advertising or publicity
 *  to endorse or promote any products or commercial entity unless specific
 *  written permission is obtained from UCAR/Unidata. The user also
 *  understands that UCAR/Unidata is not obligated to provide the user with
 *  any support, consulting, training or assistance of any kind with regard
 *  to the use, operation and performance of this software nor to provide
 *  the user with any updates, revisions, new versions or "bug fixes."
 *
 *  THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 *  INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 *  FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 *  NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 *  WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package ucar.nc2.ft2.scan;

import ucar.nc2.constants.FeatureType;
import ucar.nc2.constants._Coordinate;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.ft.FeatureDataset;
import ucar.nc2.ft.FeatureDatasetFactoryManager;
import ucar.nc2.ft2.coverage.adapter.DtCoverageCS;
import ucar.nc2.ft2.coverage.adapter.DtCoverageCSBuilder;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;

/**
 * Scan a directory, try to open files as a Feature Type dataset.
 *
 * @author caron
 * @since Aug 15, 2009
 */

public class FeatureScan {
  private String top;
  private boolean subdirs;

  public FeatureScan(String top, boolean subdirs) {
    this.top = top;
    this.subdirs = subdirs;
  }

  public java.util.List<FeatureScan.Bean> scan(Formatter errlog) {

    List<Bean> result = new ArrayList<>();

    File topFile = new File(top);
    if (!topFile.exists()) {
      errlog.format("File %s does not exist", top);
      return result;
    }

    if (topFile.isDirectory())
      scanDirectory(topFile, result);
    else {
      Bean fdb = new Bean(topFile);
      result.add(fdb);
    }

    return result;
  }

  private void scanDirectory(File dir, java.util.List<FeatureScan.Bean> result) {
    if ((dir.getName().equals("exclude")) || (dir.getName().equals("problem")))return;

    // get list of files
    File[] fila= dir.listFiles();
    if (fila == null) return;

    List<File> files = new ArrayList<>();
    for (File f : fila) {
      if (!f.isDirectory()) {
        files.add(f);
      }
    }

    // eliminate redundant files
    // ".Z", ".zip", ".gzip", ".gz", or ".bz2"
    if (files.size() > 0) {
      Collections.sort(files);
      List<File> files2 = new ArrayList<>(files);

      File prev = null;
      for (File f : files) {
        String name = f.getName();
        String stem = stem(name);
        if (name.contains(".gbx") || name.contains(".ncx") ||
                name.endsWith(".xml") || name.endsWith(".pdf") || name.endsWith(".txt") || name.endsWith(".tar")) {
          files2.remove(f);

        } else if (prev != null) {

          if (name.endsWith(".ncml")) {
            if (prev.getName().equals(stem) || prev.getName().equals(stem + ".nc"))
               files2.remove(prev);
          } else if (name.endsWith(".bz2")) {
            if (prev.getName().equals(stem)) files2.remove(f);
          } else if (name.endsWith(".gz")) {
            if (prev.getName().equals(stem)) files2.remove(f);
          } else if (name.endsWith(".gzip")) {
            if (prev.getName().equals(stem)) files2.remove(f);
          } else if (name.endsWith(".zip")) {
            if (prev.getName().equals(stem)) files2.remove(f);
          } else if (name.endsWith(".Z")) {
            if (prev.getName().equals(stem)) files2.remove(f);
          }
        }
        prev = f;
      }

      // do the remaining
      for (File f : files2) {
        result.add(new Bean(f));
      }
    }

    // do subdirs
    if (subdirs) {
      for (File f : fila) {
        if (f.isDirectory() && !f.getName().equals("exclude"))
          scanDirectory(f, result);
      }
    }

  }

  private String stem(String name) {
    int pos = name.lastIndexOf('.');
    return (pos > 0) ? name.substring(0, pos) : name;
  }

  private static final boolean debug = true;

  public class Bean {
    public File f;
    String fileType;
    String coordMap;
    FeatureType featureType, ftFromMetadata;
    String ftype;
    StringBuilder info = new StringBuilder();
    String coordSysBuilder;
    String ftImpl;
    Throwable problem;
    DtCoverageCSBuilder builder; // LOOK replace with CoverageDataset

    // no-arg constructor
    public Bean() {
    }

    public Bean(File f) {
      this.f = f;

      if (debug) System.out.printf(" featureScan=%s%n", f.getPath());
      try (NetcdfDataset ds = NetcdfDataset.openDataset(f.getPath())){
        fileType = ds.getFileTypeId();
        coordSysBuilder = ds.findAttValueIgnoreCase(null, _Coordinate._CoordSysBuilder, "none");

        Formatter errlog = new Formatter();
        builder = DtCoverageCSBuilder.classify(ds, errlog);
        info.append(errlog.toString());
        setCoordMap();

        ftFromMetadata = FeatureDatasetFactoryManager.findFeatureType(ds);

        // old
        try {
          errlog = new Formatter();
          FeatureDataset featureDataset = FeatureDatasetFactoryManager.wrap(null, ds, null, errlog);
          info.append("FeatureDatasetFactoryManager errlog = ");
          info.append(errlog.toString());
          info.append("\n\n");

          if (featureDataset != null) {
            featureType = featureDataset.getFeatureType();
            if (featureType != null)
              ftype = featureType.toString();
            ftImpl = featureDataset.getImplementationName();
            Formatter infof = new Formatter();
            featureDataset.getDetailInfo(infof);
            info.append(infof.toString());
          } else {
            ftype = "";
          }

        } catch (Throwable t) {
          ftype = " ERR: " + t.getMessage();
          info.append(errlog.toString());
          problem = t;
        }

      } catch (Throwable t) {
        fileType = " ERR: " + t.getMessage();
        problem = t;
      }
    }


    public String getName() {
      return f.getPath();
    }

    public String getFileType() {
      return fileType;
    }

    public String getSizeK() {
      Formatter fm = new Formatter();
      //long size = f.length();
      //if (size > 10 * 1000 * 1000) fm.format("%6.1f M", ((float) size) / 1000 / 1000);
      //else if (size > 10 * 1000) fm.format("%6.1f K", ((float) size) / 1000);
      //else fm.format("%d", size);
      fm.format("%,10d", f.length() / 1000);
      return fm.toString();
    }

    public String getCoordMap() {
      return coordMap;
    }

    public String getCoordSysBuilder() {
      return coordSysBuilder;
    }

    public void setCoordMap() {
      if (builder == null) return;
      DtCoverageCS cs = builder.makeCoordSys();
      if (cs == null || cs.getCoverageType() == null) return;
      coordMap = "f:D(" + cs.getDomainRank() + ")->R(" + cs.getRangeRank() + ")";
    }


    public String getFtMetadata() {
      return (ftFromMetadata == null) ? "" : ftFromMetadata.toString();
    }

    public String getFeatureType() {
      return ftype;
    }

    /* public String getFeatureImpl() {
      return ftImpl;
    } */

    public String getCoverage() {
      return builder == null ? "" : builder.showSummary();
    }

    public void toString(Formatter f, boolean showInfo) {
      f.format("%s%n %s%n map = '%s'%n", getName(), getFileType(), getCoordMap());
      if (builder != null) f.format("%n%s%n", builder.toString());
      // f.format("%s%n", builder.makeCoordSys()); LOOK would have to reopen

      if (showInfo && info != null) {
        f.format("%n%s", info);
      }
      if (problem != null) {
        StringWriter sw = new StringWriter(5000);
        problem.printStackTrace(new PrintWriter(sw));
        f.format(sw.toString());
      }
    }

    public String toString() {
      Formatter f = new Formatter();
      toString(f, true);
      return f.toString();
    }

    public String runClassifier() {
      Formatter ff = new Formatter();
      String type = null;
      try (NetcdfDataset ds = NetcdfDataset.openDataset(f.getPath())) {
        type = DtCoverageCSBuilder.describe(ds, ff);

      } catch (IOException e) {
        StringWriter sw = new StringWriter(10000);
        e.printStackTrace(new PrintWriter(sw));
        ff.format("%n%s", sw.toString());
      }
      ff.format("CoverageCS.Type = %s", type);
      return ff.toString();
    }
  }

  public static void main(String arg[]) {
    String usage = "usage: ucar.nc2.ft.scan.FeatureScan directory [-subdirs]";
    if (arg.length < 1) {
      System.out.println(usage);
      System.exit(0);
    }

    boolean subdirs = false;

    for (int i = 1; i < arg.length; i++) {
      String s = arg[i];
      if (s.equalsIgnoreCase("-subdirs")) subdirs = true;
    }

    FeatureScan scanner = new FeatureScan(arg[0], subdirs);

    System.out.printf(" %-60s %-20s %-10s %-10s%n", "name", "fileType", "featureType", "featureImpl");
    List<FeatureScan.Bean> beans = scanner.scan(new Formatter());
    for (Bean b : beans)
      System.out.printf(" %-60s %-20s %n", b.getName(), b.getFileType());

  }
}
