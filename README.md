# LaundryLink — Setup Guide

From `git clone` to a running app. Do the steps in order; each one only needs to be done once.

---

## 1. Install the tools

| Tool | Version | Notes |
|---|---|---|
| **JDK 8 with JavaFX** | 8 (1.8) | Use **Oracle JDK 8**, **Liberica JDK 8 Full**, or **Azul Zulu 8 JDK FX**. A plain OpenJDK 8 has **no JavaFX** and the project won't build. |
| **Apache NetBeans** | **8.2** | The project is a NetBeans 8.2 JavaFX (Ant) project. |
| **MySQL Community Server** | 8.0 | Write down the **root** password you choose during install. |
| **MySQL Workbench** | 8.0 | Used to run the database scripts. |
| **Git** | any | To download the project. |

The MySQL driver is already in the project (`lib/`). You don't need to download it.

---

## 2. Download the project

Open **Git Bash** in your NetBeans projects folder (usually `Documents/NetBeansProjects`) and run:

```
git clone https://github.com/stagnantwater69/LaundryLink.git
```

This creates a `LaundryLink` folder.

---

## 3. Open it in NetBeans

1. Start **NetBeans 8.2**.
2. **File → Open Project…** → select the `LaundryLink` folder → **Open Project**.
3. Check the Java version: **Tools → Java Platforms** must list a **JDK 1.8** with JavaFX. If NetBeans shows a "missing platform" or "resolve problems" message, right-click the project → **Resolve Project Problems…** and pick your JDK 8.

---

## 4. Create the database

1. Make sure MySQL is running: open Windows **Services**, find **MySQL80**, and click **Start** if it's stopped.
2. Open **MySQL Workbench** and connect to **Local instance** as **root**.
3. Run the 3 scripts in the project's `database` folder **in this order**. For each one: **File → Open SQL Script…** → choose the file → click the ⚡ **Execute** button (the plain lightning bolt that runs the whole script, *not* the one with the cursor).

| Order | Script | What it does |
|---|---|---|
| 1 | `schema.sql` | Creates the `laundrylink` database and its tables |
| 2 | `create-app-user.sql` | Creates the login the app uses. **Before executing, replace `change-me` (line 8) with a password of your own.** Remember it for step 5. |
| 3 | `sample-data.sql` | Adds sample laundry services and customers |

To check it worked: in Workbench's **Schemas** panel, right-click → **Refresh All**. You should see a `laundrylink` database with 7 tables.

---

## 5. Connect the app to your database

1. Open the project's `config` folder.
2. Copy `database.properties.example` and rename the copy to **`database.properties`** (same folder).
3. Open `database.properties` and set the password from step 4:

```properties
db.url=jdbc:mysql://localhost:3306/laundrylink
db.user=laundrylink_app
db.password=YOUR-PASSWORD-FROM-STEP-4
```

`database.properties` is ignored by Git, so your password is never uploaded. **Don't put a real password in the `.example` file.**

---

## 6. Run the app

1. In NetBeans, right-click **LaundryLink → Clean and Build**. Wait for **BUILD SUCCESSFUL** at the bottom.
2. Press **F6** (or the green ▶ **Run Project** button).
3. The **Welcome** screen opens.

### First run: create the owner account
1. Click **Staff / Owner Login**.
2. Your database has no accounts yet, so **Owner Setup** opens. Enter a first name, middle name (optional), last name, username, and a password (at least 8 characters). Click **Create Owner Account**.
3. Log in with that username and password. The **Dashboard** opens.

This owner account exists only in *your* local database. To test as a staff member, go to the **Staff Accounts** tab and create one.

From now on, just press **F6** to run.

---

## 7. Troubleshooting

| Problem | Fix |
|---|---|
| "Could not reach the LaundryLink database" | MySQL isn't running → start **MySQL80** in Windows Services. |
| "Access denied for user 'laundrylink_app'" | The password in `config/database.properties` doesn't match the one you used in `create-app-user.sql`. Make them the same. |
| "Unknown database 'laundrylink'" or "Table … doesn't exist" | Step 4 wasn't completed. Run `schema.sql` again. |
| Build errors mentioning `javafx` | Your JDK doesn't include JavaFX. Install one from step 1 and select it in **Tools → Java Platforms**. |
| "Unable to delete file … dist" when building | The app is still running. Close its window and build again. |
| Old screens appear after pulling new code | Do **Clean and Build**, then run. |
| Forgot the owner password (local test database, no orders yet) | In Workbench (as root): `DELETE FROM laundrylink.users WHERE id > 0;` then run the app again to repeat Owner Setup. *This removes all accounts in your local database, and only works while no orders exist.* |

---

## 8. Getting the latest code later

```
git pull
```

Then in NetBeans: **Clean and Build** → **F6**.

If someone changes the database structure, they'll share an update script. Run it in Workbench the same way as in step 4.
