package com.minecrafttas.lotas_light.savestates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.Logger;

import com.minecrafttas.lotas_light.LoTASLight;
import com.minecrafttas.lotas_light.config.AbstractDataFile;
import com.minecrafttas.lotas_light.savestates.exceptions.LoadstateException;
import com.minecrafttas.lotas_light.savestates.exceptions.SavestateException;

import net.minecraft.world.phys.Vec3;

/**
 * Manages the savestates on the filesystem and assignes new indizes
 * 
 * @author Scribble
 */
public class SavestateIndexer {

	private final Logger logger;
	private final Path savestateBaseDirectory;
	private Path savesDir;
	private final String worldname;
	private final Path currentSavestateDir;
	private final LinkedHashMap<Integer, Savestate> savestateList;
	private Savestate currentSavestate;

	private static final Path savestateDatPath = Path.of("tas/savestate.xml");

	public SavestateIndexer(Logger logger, Path savesDir, Path savestateBaseDirectory, String worldname) {
		this.logger = logger;
		this.savestateBaseDirectory = savestateBaseDirectory;
		this.savesDir = savesDir;
		this.worldname = worldname;
		this.currentSavestateDir = savestateBaseDirectory.resolve(String.format("%s-Savestates", worldname));
		savestateList = new LinkedHashMap<>();
		createSavestateDir();
		currentSavestate = new Savestate(savesDir.resolve(worldname).resolve(savestateDatPath));
		currentSavestate.loadFromXML();
		reload();
	}

	private void createSavestateDir() {
		try {
			Files.createDirectories(currentSavestateDir);
		} catch (IOException e) {
			logger.catching(e);
		}
	}

	public SavestatePaths createSavestate(int index, Vec3 motion) {
		return createSavestate(index, null, true, motion);
	}

	public SavestatePaths createSavestate(int index, String name, boolean changeIndex, Vec3 motion) {
		logger.trace("Creating savestate in indexer");
		if (index < 0) {
			index = currentSavestate.getIndex() + 1;
		}

		if (name == null) {
			name = "Savestate #" + index;
		}

		int savedIndex = index;

		currentSavestate.index = index;
		currentSavestate.name = name;
		currentSavestate.date = new Date();

		currentSavestate.saveToXML();

		if (!changeIndex)
			currentSavestate.index = savedIndex;

		savestateList.put(index, currentSavestate.clone());
		sortSavestateList();

		Path sourceDir = savesDir.resolve(worldname);
		Path targetDir = currentSavestateDir.resolve(worldname + index);

		return SavestatePaths.of(currentSavestate.clone(), sourceDir, targetDir);
	}

	public SavestatePaths loadSavestate(int index, boolean changeIndex) throws LoadstateException {
		logger.trace("Loading savestate in indexer");
		if (index < 0) {
			index = currentSavestate.getIndex();
		}

		Savestate savestateToLoad = savestateList.get(index);

		if (savestateToLoad == null) {
			throw new LoadstateException("Savestate %s does not exist", index);
		}

		int savedIndex = currentSavestate.index;
		this.currentSavestate = savestateToLoad.clone();

		Path sourceDir = currentSavestateDir.resolve(worldname + currentSavestate.index);
		Path targetDir = savesDir.resolve(worldname);

		SavestatePaths out = SavestatePaths.of(currentSavestate.clone(), sourceDir, targetDir);

		if (!changeIndex)
			currentSavestate.index = savedIndex;

		return out;
	}

	private void sortSavestateList() {
		LinkedHashMap<Integer, Savestate> copy = new LinkedHashMap<>();
		//@formatter:off
		savestateList.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> copy.put(entry.getKey(), entry.getValue()));
		//@formatter:on
		savestateList.clear();
		savestateList.putAll(copy);
	}

	public void reload() {
		logger.trace("Reloading savestate indexes");
		savestateList.clear();
		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				Stream<Path> stream = null;
				try {
					stream = Files.list(currentSavestateDir); // Get a list of paths in the specified directory
				} catch (IOException e) {
					logger.catching(e);
					return;
				}

				//@formatter:off
				Set<Path> pathSet = stream
						.filter(file -> Files.isDirectory(file))
						.filter(file -> file.getFileName().toString().startsWith(worldname))
						.collect(Collectors.toSet());
				//@formatter:on

				stream.close();

				Pattern pattern = Pattern.compile(worldname + "(\\d)$");

				pathSet.forEach(path -> {
					Path savestateDat = path.resolve(savestateDatPath);

					Savestate savestate = null;
					if (!Files.exists(savestateDat)) {

						Matcher matcher = pattern.matcher(path.getFileName().toString());
						int backupIndex = -1;
						if (matcher.find()) {
							backupIndex = Integer.parseInt(matcher.group(1));
						}

						logger.warn("Savestate {} does not contain a valid savestate.xml, skipping", backupIndex);
						Throwable error = new SavestateException("Savestate.xml data file not found in " + savestateBaseDirectory.relativize(savestateDat));
						savestate = new FailedSavestate(path, backupIndex, null, null, error);
					} else {
						savestate = new Savestate(savestateDat);
						savestate.loadFromXML();
					}
					savestateList.put(savestate.getIndex(), savestate.clone());
					sortSavestateList();
				});
			}
		}, "Savestate Reload");
		t.run();
	}

	public Set<Integer> getIndexList() {
		return savestateList.keySet();
	}

	public List<Savestate> getSavestateList(int amount) {
		List<Savestate> out = new LinkedList<>();
		if (amount <= 0) {
			savestateList.forEach((key, value) -> out.add(value));
			return out;
		}

		LinkedHashMap<Integer, Savestate> copy = new LinkedHashMap<>(savestateList);
		for (int i = 0; i < amount; i++) {
			Entry<Integer, Savestate> entry = copy.pollLastEntry();
			if (entry == null)
				break;
			out.addFirst(entry.getValue());
		}
		return out;
	}

	public Savestate getCurrentSavestate() {
		return currentSavestate;
	}

	public class Savestate extends AbstractDataFile {

		protected Integer index;
		protected String name;
		protected Date date;
		protected Vec3 motion;

		private Savestate(Path file) {
			this(file, -1, null, null, null);
		}

		private Savestate(Path file, Integer index, String name, Date date, Vec3 motion) {
			super(LoTASLight.LOGGER, file, "Savestate", "Stores savestate related data");
			this.index = index;
			this.name = name;
			this.date = date;
			this.motion = motion;
		}

		private Savestate(Path file, Properties properties, Integer index, String name, Date date, Vec3 motion) {
			this(file, index, name, date, motion);
			this.properties = properties;
		}

		private enum Options {
			INDEX,
			NAME,
			DATE,
			MOTION_X,
			MOTION_Y,
			MOTION_Z;

			@Override
			public String toString() {
				return super.toString().toLowerCase();
			}
		}

		public Integer getIndex() {
			return index;
		}

		public String getName() {
			return name;
		}

		public Date getDate() {
			return date;
		}

		public Vec3 getMotion() {
			return motion;
		}

		@Override
		public void saveToXML() {
			properties.setProperty(Options.INDEX.toString(), Integer.toString(index));
			properties.setProperty(Options.NAME.toString(), name);
			properties.setProperty(Options.DATE.toString(), Long.toString(ChronoUnit.SECONDS.between(Instant.EPOCH, date.toInstant())));
			if (motion != null) {
				properties.setProperty(Options.MOTION_X.toString(), Double.toString(motion.x));
				properties.setProperty(Options.MOTION_Y.toString(), Double.toString(motion.y));
				properties.setProperty(Options.MOTION_Z.toString(), Double.toString(motion.z));
			}
			super.saveToXML();
		}

		@Override
		public void loadFromXML() {
			super.loadFromXML();
			try {
				this.index = Integer.parseInt(properties.getProperty(Options.INDEX.toString()));
			} catch (Exception e) {
				logger.error("Can't parse '{}' in {}", Options.INDEX.toString(), currentSavestateDir.resolve(savestateDatPath));
				logger.catching(e);
			}
			this.name = properties.getProperty(Options.NAME.toString());
			try {
				this.date = parseDate(properties.getProperty(Options.DATE.toString()));
			} catch (Exception e) {
				logger.error("Can't parse '{}' in {}", Options.DATE.toString(), currentSavestateDir.resolve(savestateDatPath));
				logger.catching(e);
			}

			String x = properties.getProperty(Options.MOTION_X.toString());
			String y = properties.getProperty(Options.MOTION_Y.toString());
			String z = properties.getProperty(Options.MOTION_Z.toString());
			if (x != null && y != null && z != null) {
				try {
					this.motion = new Vec3(Double.parseDouble(x), Double.parseDouble(y), Double.parseDouble(z));
				} catch (Exception e) {
					logger.error("Can't parse '{}' in {}", Options.DATE.toString(), currentSavestateDir.resolve(savestateDatPath));
					logger.catching(e);
				}
			}
		}

		@Override
		protected Savestate clone() {
			return new Savestate(file, properties, index, name, date, motion);
		}

		private static Date parseDate(String dateString) throws Exception {
			long unixTimestamp = Long.parseLong(dateString);
			return Date.from(Instant.ofEpochSecond(unixTimestamp));
		}
	}

	public class FailedSavestate extends Savestate {

		private final Throwable t;

		public FailedSavestate(Path file, Throwable t) {
			this(file, null, null, null, t);
		}

		public FailedSavestate(Path file, Integer index, String name, Date date, Throwable t) {
			super(file, index, name, date, null);
			this.t = t;
		}

		public FailedSavestate(Path file, Properties properties, Integer index, String name, Date date, Throwable t) {
			super(file, index, name, date, null);
			this.t = t;
		}

		public Throwable getError() {
			return t;
		}

		@Override
		public void saveToXML() {
		}

		@Override
		public void save() {
		}

		@Override
		public void loadFromXML() {
		}

		@Override
		public void load() {
		}

		@Override
		protected FailedSavestate clone() {
			return new FailedSavestate(file, properties, index, name, date, t);
		}
	}

	public static class SavestatePaths {
		private final Savestate savestate;
		private final Path sourceFolder;
		private final Path targetFolder;

		private SavestatePaths(Savestate savestate, Path sourceFolder, Path targetFolder) {
			this.savestate = savestate;
			this.sourceFolder = sourceFolder;
			this.targetFolder = targetFolder;
		}

		public Savestate getSavestate() {
			return savestate;
		}

		public Path getSourceFolder() {
			return sourceFolder;
		}

		public Path getTargetFolder() {
			return targetFolder;
		}

		public static SavestatePaths of(Savestate savestate, Path sourceFolder, Path targetFolder) {
			return new SavestatePaths(savestate, sourceFolder, targetFolder);
		}
	}
}
