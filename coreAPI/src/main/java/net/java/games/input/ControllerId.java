/*
 * Copyright (c) 2020, Wolfgang Reder
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.java.games.input;

import java.util.Objects;

/**
 *
 * @author Wolfgang Reder
 */
public final class ControllerId
{

  private final String type;
  private final String bustype;
  private final String vendor;
  private final String device;
  private final String version;

  public ControllerId(String type,
                      String bus,
                      String vendor,
                      String device,
                      String version)
  {
    this.type = type;
    this.bustype = bus;
    this.vendor = vendor;
    this.device = device;
    this.version = version;
  }

  public String getBusType()
  {
    return bustype;
  }

  public String getVendor()
  {
    return vendor;
  }

  public String getDevice()
  {
    return device;
  }

  public String getVersion()
  {
    return version;
  }

  @Override
  public int hashCode()
  {
    int hash = 7;
    hash = 79 * hash + Objects.hashCode(this.type);
    hash = 79 * hash + Objects.hashCode(this.bustype);
    hash = 79 * hash + Objects.hashCode(this.vendor);
    hash = 79 * hash + Objects.hashCode(this.device);
    hash = 79 * hash + Objects.hashCode(this.version);
    return hash;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ControllerId other = (ControllerId) obj;
    if (!Objects.equals(this.type,
                        other.type)) {
      return false;
    }
    if (!Objects.equals(this.bustype,
                        other.bustype)) {
      return false;
    }
    if (!Objects.equals(this.vendor,
                        other.vendor)) {
      return false;
    }
    if (!Objects.equals(this.device,
                        other.device)) {
      return false;
    }
    if (!Objects.equals(this.version,
                        other.version)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString()
  {
    return type + ":" + bustype + ":" + vendor + ":" + device + ":" + version;
  }

}
