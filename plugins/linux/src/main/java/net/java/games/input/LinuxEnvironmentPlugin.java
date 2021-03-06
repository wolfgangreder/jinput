/*
 * Copyright (C) 2003 Jeremy Booth (jeremy@newdawnsoftware.com)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer. Redistributions in binary
 * form must reproduce the above copyright notice, this list of conditions and
 * the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 * The name of the author may not be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE
 */
package net.java.games.input;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import net.java.games.util.plugins.Plugin;

/**
 * Environment plugin for linux
 *
 * @author elias
 * @author Jeremy Booth (jeremy@newdawnsoftware.com)
 */
public final class LinuxEnvironmentPlugin extends ControllerEnvironment implements Plugin
{

  private final static String LIBNAME = "jinput-linux";
  private final static String POSTFIX64BIT = "64";
  private final static String POSTFIXARM32 = "_arm";
  private final static String POSTFIXARM64 = "_arm64";
  private static boolean supported = false;
  private final Object lock = new Object();
  private List<Controller> _controllers;
  private List<LinuxDevice> _devices = new ArrayList<>();
  private final static LinuxDeviceThread _device_thread = new LinuxDeviceThread();

  /**
   * Static utility method for loading native libraries. It will try to load from either the path given by the
   * net.java.games.input.librarypath property or through System.loadLibrary().
   *
   */
  static void loadLibrary(final String lib_name)
  {
    AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
      String lib_path = System.getProperty("net.java.games.input.librarypath");
      try {
        if (lib_path != null) {
          System.load(lib_path + File.separator + System.mapLibraryName(lib_name));
        } else {
          System.loadLibrary(lib_name);
        }
      } catch (UnsatisfiedLinkError e) {
        log("Failed to load library: " + e.getMessage());
        LOGGER.log(Level.SEVERE,
                   "Failed to load library: " + lib_name,
                   e);
        supported = false;
      }
      return null;
    });
  }

  static String getPrivilegedProperty(final String property)
  {
    return AccessController.doPrivileged((PrivilegedAction<String>) () -> System.getProperty(property));
  }

  static String getPrivilegedProperty(final String property,
                                      final String default_value)
  {
    return AccessController.doPrivileged((PrivilegedAction<String>) () -> System.getProperty(property,
                                                                                             default_value));
  }

  static String exposeLib(String libName)
  {
    String mappedName = System.mapLibraryName(libName);
    InputStream strm = LinuxEnvironmentPlugin.class.getResourceAsStream("/" + mappedName);
    if (strm != null) {
      try {
        File tmpFile = File.createTempFile("jinput",
                                           ".so");
        tmpFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
          strm.transferTo(fos);
        }
        return tmpFile.getAbsolutePath();
      } catch (IOException ex) {
        LOGGER.log(Level.SEVERE,
                   null,
                   ex);
      }
    }
    return null;
  }

  static {
    String osName = getPrivilegedProperty("os.name",
                                          "").trim();
    if (osName.equals("Linux")) {
      String arch = getPrivilegedProperty("os.arch");
      String dataModel = getPrivilegedProperty("sun.arch.data.model");
      String libName;
      switch (arch) {
        case "i386":
          libName = LIBNAME;
          break;
        case "arm":
          if ("64".equals(dataModel)) {
            libName = LIBNAME + POSTFIXARM64;
          } else {
            libName = LIBNAME + POSTFIXARM32;
          }
          break;
        default:
          libName = LIBNAME + POSTFIX64BIT;
      }
      supported = libName != null;
      if (libName != null) {
        LOGGER.log(Level.INFO,
                   "Loading native lib {0}",
                   libName);
        String tmpFile = exposeLib(libName);
        if (tmpFile != null) {
          Runtime.getRuntime().load(tmpFile);
        } else {
          loadLibrary(libName);
        }
      } else {
        LOGGER.log(Level.SEVERE,
                   "No supported linux {0} {1}",
                   new Object[]{arch, dataModel});
      }
    }
  }

  public final static Object execute(LinuxDeviceTask task) throws IOException
  {
    return _device_thread.execute(task);
  }

  public LinuxEnvironmentPlugin()
  {
    if (isSupported()) {
      this._controllers = enumerateControllers();
      log("Linux plugin claims to have found " + _controllers.size() + " controllers");
      AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
        Runtime.getRuntime().addShutdownHook(new ShutdownHook());
        return null;
      });
    } else {
      _controllers = Collections.emptyList();
    }
  }

  /**
   * Returns a list of all controllers available to this environment, or an empty array if there are no controllers in this
   * environment.
   *
   * @return Returns a list of all controllers available to this environment, or an empty array if there are no controllers in
   * this environment.
   */
  @Override
  public final List<Controller> getControllers()
  {
    List<Controller> result;
    synchronized (lock) {
      result = new ArrayList<>(_controllers);
    }
    return result;
  }

  private static Component[] createComponents(List<LinuxEventComponent> event_components,
                                              LinuxEventDevice device)
  {
    LinuxEventComponent[][] povs = new LinuxEventComponent[4][2];
    List<LinuxComponent> components = new ArrayList<>();
    for (int i = 0; i < event_components.size(); i++) {
      LinuxEventComponent event_component = event_components.get(i);
      Component.Identifier identifier = event_component.getIdentifier();

      if (identifier == Component.Identifier.Axis.POV) {
        int native_code = event_component.getDescriptor().getCode();
        switch (native_code) {
          case NativeDefinitions.ABS_HAT0X:
            povs[0][0] = event_component;
            break;
          case NativeDefinitions.ABS_HAT0Y:
            povs[0][1] = event_component;
            break;
          case NativeDefinitions.ABS_HAT1X:
            povs[1][0] = event_component;
            break;
          case NativeDefinitions.ABS_HAT1Y:
            povs[1][1] = event_component;
            break;
          case NativeDefinitions.ABS_HAT2X:
            povs[2][0] = event_component;
            break;
          case NativeDefinitions.ABS_HAT2Y:
            povs[2][1] = event_component;
            break;
          case NativeDefinitions.ABS_HAT3X:
            povs[3][0] = event_component;
            break;
          case NativeDefinitions.ABS_HAT3Y:
            povs[3][1] = event_component;
            break;
          default:
            log("Unknown POV instance: " + native_code);
            break;
        }
      } else if (identifier != null) {
        LinuxComponent component = new LinuxComponent(event_component);
        components.add(component);
        device.registerComponent(event_component.getDescriptor(),
                                 component);
      }
    }
    for (LinuxEventComponent[] pov : povs) {
      LinuxEventComponent x = pov[0];
      LinuxEventComponent y = pov[1];
      if (x != null && y != null) {
        LinuxComponent controller_component = new LinuxPOV(x,
                                                           y);
        components.add(controller_component);
        device.registerComponent(x.getDescriptor(),
                                 controller_component);
        device.registerComponent(y.getDescriptor(),
                                 controller_component);
      }
    }
    Component[] components_array = new Component[components.size()];
    components.toArray(components_array);
    return components_array;
  }

  private static Mouse createMouseFromDevice(LinuxEventDevice device,
                                             Component[] components) throws IOException
  {
    Mouse mouse = new LinuxMouse(device,
                                 components,
                                 new Controller[]{},
                                 device.getRumblers());
    if (mouse.getX() != null && mouse.getY() != null && mouse.getPrimaryButton() != null) {
      return mouse;
    } else {
      return null;
    }
  }

  private static Keyboard createKeyboardFromDevice(LinuxEventDevice device,
                                                   Component[] components) throws IOException
  {
    Keyboard keyboard = new LinuxKeyboard(device,
                                          components,
                                          new Controller[]{},
                                          device.getRumblers());
    return keyboard;
  }

  private static Controller createJoystickFromDevice(LinuxEventDevice device,
                                                     Component[] components,
                                                     Controller.Type type) throws IOException
  {
    Controller joystick = new LinuxAbstractController(device,
                                                      components,
                                                      new Controller[]{},
                                                      device.getRumblers(),
                                                      type);
    return joystick;
  }

  private static Controller createControllerFromDevice(LinuxEventDevice device) throws IOException
  {
    List<LinuxEventComponent> event_components = device.getComponents();
    Component[] components = createComponents(event_components,
                                              device);
    Controller.Type type = device.getType();

    if (type == Controller.Type.MOUSE) {
      return createMouseFromDevice(device,
                                   components);
    } else if (type == Controller.Type.KEYBOARD) {
      return createKeyboardFromDevice(device,
                                      components);
    } else if (type == Controller.Type.STICK || type == Controller.Type.GAMEPAD) {
      return createJoystickFromDevice(device,
                                      components,
                                      type);
    } else {
      return null;
    }
  }

  @Override
  public void rescanController()
  {
    synchronized (lock) {
      closeAllDevices();
      _controllers = enumerateControllers();
    }
  }

  private List<Controller> enumerateControllers()
  {
    List<Controller> controllers = new ArrayList<>();
    List<Controller> eventControllers = new ArrayList<>();
    List<Controller> jsControllers = new ArrayList<>();
    enumerateEventControllers(eventControllers);
    enumerateJoystickControllers(jsControllers);

    for (int i = 0; i < eventControllers.size(); i++) {
      for (int j = 0; j < jsControllers.size(); j++) {
        Controller evController = eventControllers.get(i);
        Controller jsController = jsControllers.get(j);

        // compare
        // Check if the nodes have the same name
        if (evController.getName().equals(jsController.getName())) {
          // Check they have the same component count
          Component[] evComponents = evController.getComponents();
          Component[] jsComponents = jsController.getComponents();
          if (evComponents.length == jsComponents.length) {
            boolean foundADifference = false;
            // check the component pairs are of the same type
            for (int k = 0; k < evComponents.length; k++) {
              // Check the type of the component is the same
              if (!(evComponents[k].getIdentifier() == jsComponents[k].getIdentifier())) {
                foundADifference = true;
              }
            }

            if (!foundADifference) {
              controllers.add(new LinuxCombinedController((LinuxAbstractController) eventControllers.remove(i),
                                                          (LinuxJoystickAbstractController) jsControllers.remove(j)));
              i--;
              j--;
              break;
            }
          }
        }
      }
    }
    controllers.addAll(eventControllers);
    controllers.addAll(jsControllers);
    return controllers;
  }

  private static Component.Identifier.Button getButtonIdentifier(int index)
  {
    switch (index) {
      case 0:
        return Component.Identifier.Button._0;
      case 1:
        return Component.Identifier.Button._1;
      case 2:
        return Component.Identifier.Button._2;
      case 3:
        return Component.Identifier.Button._3;
      case 4:
        return Component.Identifier.Button._4;
      case 5:
        return Component.Identifier.Button._5;
      case 6:
        return Component.Identifier.Button._6;
      case 7:
        return Component.Identifier.Button._7;
      case 8:
        return Component.Identifier.Button._8;
      case 9:
        return Component.Identifier.Button._9;
      case 10:
        return Component.Identifier.Button._10;
      case 11:
        return Component.Identifier.Button._11;
      case 12:
        return Component.Identifier.Button._12;
      case 13:
        return Component.Identifier.Button._13;
      case 14:
        return Component.Identifier.Button._14;
      case 15:
        return Component.Identifier.Button._15;
      case 16:
        return Component.Identifier.Button._16;
      case 17:
        return Component.Identifier.Button._17;
      case 18:
        return Component.Identifier.Button._18;
      case 19:
        return Component.Identifier.Button._19;
      case 20:
        return Component.Identifier.Button._20;
      case 21:
        return Component.Identifier.Button._21;
      case 22:
        return Component.Identifier.Button._22;
      case 23:
        return Component.Identifier.Button._23;
      case 24:
        return Component.Identifier.Button._24;
      case 25:
        return Component.Identifier.Button._25;
      case 26:
        return Component.Identifier.Button._26;
      case 27:
        return Component.Identifier.Button._27;
      case 28:
        return Component.Identifier.Button._28;
      case 29:
        return Component.Identifier.Button._29;
      case 30:
        return Component.Identifier.Button._30;
      case 31:
        return Component.Identifier.Button._31;
      default:
        return null;
    }
  }

  private static Controller createJoystickFromJoystickDevice(LinuxJoystickDevice device)
  {
    List<AbstractComponent> components = new ArrayList<>();
    byte[] axisMap = device.getAxisMap();
    char[] buttonMap = device.getButtonMap();
    LinuxJoystickAxis[] hatBits = new LinuxJoystickAxis[6];

    for (int i = 0; i < device.getNumButtons(); i++) {
      Component.Identifier button_id = LinuxNativeTypesMap.getButtonID(buttonMap[i]);
      if (button_id != null) {
        LinuxJoystickButton button = new LinuxJoystickButton(button_id);
        device.registerButton(i,
                              button);
        components.add(button);
      }
    }
    for (int i = 0; i < device.getNumAxes(); i++) {
      Component.Identifier.Axis axis_id;
      axis_id = (Component.Identifier.Axis) LinuxNativeTypesMap.getAbsAxisID(axisMap[i]);
      LinuxJoystickAxis axis = new LinuxJoystickAxis(axis_id);

      device.registerAxis(i,
                          axis);

      if (axisMap[i] == NativeDefinitions.ABS_HAT0X) {
        hatBits[0] = axis;
      } else if (axisMap[i] == NativeDefinitions.ABS_HAT0Y) {
        hatBits[1] = axis;
        axis = new LinuxJoystickPOV(Component.Identifier.Axis.POV,
                                    hatBits[0],
                                    hatBits[1]);
        device.registerPOV((LinuxJoystickPOV) axis);
        components.add(axis);
      } else if (axisMap[i] == NativeDefinitions.ABS_HAT1X) {
        hatBits[2] = axis;
      } else if (axisMap[i] == NativeDefinitions.ABS_HAT1Y) {
        hatBits[3] = axis;
        axis = new LinuxJoystickPOV(Component.Identifier.Axis.POV,
                                    hatBits[2],
                                    hatBits[3]);
        device.registerPOV((LinuxJoystickPOV) axis);
        components.add(axis);
      } else if (axisMap[i] == NativeDefinitions.ABS_HAT2X) {
        hatBits[4] = axis;
      } else if (axisMap[i] == NativeDefinitions.ABS_HAT2Y) {
        hatBits[5] = axis;
        axis = new LinuxJoystickPOV(Component.Identifier.Axis.POV,
                                    hatBits[4],
                                    hatBits[5]);
        device.registerPOV((LinuxJoystickPOV) axis);
        components.add(axis);
      } else {
        components.add(axis);
      }
    }

    return new LinuxJoystickAbstractController(device,
                                               components.toArray(new Component[]{}),
                                               new Controller[]{},
                                               new Rumbler[]{});
  }

  private void enumerateJoystickControllers(List<Controller> controllers)
  {
    File[] joystick_device_files = enumerateJoystickDeviceFiles("/dev/input");
    if (joystick_device_files == null || joystick_device_files.length == 0) {
      joystick_device_files = enumerateJoystickDeviceFiles("/dev");
      if (joystick_device_files == null) {
        return;
      }
    }
    List<LinuxDevice> tmp = new ArrayList<>();
    for (File event_file : joystick_device_files) {
      try {
        String path = getAbsolutePathPrivileged(event_file);
        LinuxJoystickDevice device = new LinuxJoystickDevice(path);
        Controller controller = createJoystickFromJoystickDevice(device);
        if (controller != null) {
          controllers.add(controller);
          tmp.add(device);
        } else {
          device.close();
        }
      } catch (IOException e) {
        log("Failed to open device (" + event_file + "): " + e.getMessage());
      }
    }
    synchronized (lock) {
      _devices.addAll(tmp);
    }
  }

  private static File[] enumerateJoystickDeviceFiles(final String dev_path)
  {
    final File dev = new File(dev_path);
    return listFilesPrivileged(dev,
                               (File dir, String name) -> name.startsWith("js"));
  }

  private static String getAbsolutePathPrivileged(final File file)
  {
    return AccessController.doPrivileged((PrivilegedAction<String>) () -> file.getAbsolutePath());
  }

  private static File[] listFilesPrivileged(final File dir,
                                            final FilenameFilter filter)
  {
    return AccessController.doPrivileged((PrivilegedAction<File[]>) () -> {
      File[] files = dir.listFiles(filter);
      if (files == null) {
        log("dir " + dir.getName() + " exists: " + dir.exists() + ", is writable: " + dir.isDirectory());
        files = new File[]{};
      } else {
        Arrays.sort(files,
                    Comparator.comparing(File::getName));
      }
      return files;
    });
  }

  private void enumerateEventControllers(List<Controller> controllers)
  {
    final File dev = new File("/dev/input");
    File[] event_device_files = listFilesPrivileged(dev,
                                                    (File dir, String name) -> name.startsWith("event"));

    if (event_device_files == null) {
      return;
    }
    List<LinuxDevice> tmp = new ArrayList<>();
    for (File event_file : event_device_files) {
      try {
        String path = getAbsolutePathPrivileged(event_file);
        LinuxEventDevice device = new LinuxEventDevice(path);
        try {
          Controller controller = createControllerFromDevice(device);
          if (controller != null) {
            controllers.add(controller);
            tmp.add(device);
          } else {
            device.close();
          }
        } catch (IOException e) {
          log("Failed to create Controller: " + e.getMessage());
          device.close();
        }
      } catch (IOException e) {
        log("Failed to open device (" + event_file + "): " + e.getMessage());
      }
    }
    synchronized (lock) {
      _devices.addAll(tmp);
    }
  }

  private void closeAllDevices()
  {
    List<LinuxDevice> tmp;
    synchronized (lock) {
      tmp = new ArrayList<>(_devices);
    }
    for (LinuxDevice device : tmp) {
      try {
        device.close();
      } catch (IOException e) {
        log("Failed to close device: " + e.getMessage());
      }
    }
  }

  private final class ShutdownHook extends Thread
  {

    @Override
    public final void run()
    {
      closeAllDevices();
    }

  }

  @Override
  public boolean isSupported()
  {
    return supported;
  }

}
