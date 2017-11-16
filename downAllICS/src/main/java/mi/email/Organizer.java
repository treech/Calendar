package mi.email;

public class Organizer {

	public String mName;
	public String mEmail;

	public Organizer(String name, String email) {
		if (name != null) {
			mName = name;
		} else {
			mName = "UNKNOWN";
		}
		if (email != null) {
			mEmail = email;
		} else {
			mEmail = "UNKNOWN";
		}
	}

	/**
	 * Returns an iCal formatted string
	 */
	public String getICalFormattedString() {
		StringBuilder output = new StringBuilder();
		// Add the organizer info
		output.append("ORGANIZER;CN=" + mName + ":MAILTO:" + mEmail);
		// Enforce line length constraints
		output = enforceICalLineLength(output);
		output.append("\n");
		return output.toString();
	}

	public static Organizer populateFromICalString(String iCalFormattedString) {
		// TODO: Add sanity checks
		String[] organizer = iCalFormattedString.split(";");
		String[] entries = organizer[1].split(":");
		String name = entries[0].replace("CN=", "");
		String email = entries[1].replace("MAILTO=", "");
		return new Organizer(name, email);
	}

	/**
	 * Formats the given input to adhere to the iCal line length and formatting requirements
	 * @param input
	 * @return
	 */
	public static StringBuilder enforceICalLineLength(StringBuilder input) {
		final int sPermittedLineLength = 75; // Line length mandated by iCalendar format

		if (input == null) return null;
		StringBuilder output = new StringBuilder();
		int length = input.length();

		// Bail if no work needs to be done
		if (length <= sPermittedLineLength) {
			return input;
		}

		for (int i = 0, currentLineLength = 0; i < length; i++) {
			char currentChar = input.charAt(i);
			if (currentChar == '\n') {          // New line encountered
				output.append(currentChar);
				currentLineLength = 0;          // Reset char counter

			} else if (currentChar != '\n' && currentLineLength <= sPermittedLineLength) {
				// A non-newline char that can be part of the current line
				output.append(currentChar);
				currentLineLength++;

			} else if (currentLineLength > sPermittedLineLength) {
				// Need to branch out to a new line
				// Add a new line and a space - iCal requirement
				output.append("\n ");
				output.append(currentChar);
				currentLineLength = 2;          // Already has 2 chars: space and currentChar
			}
		}

		return output;
	}
}
