# Use the official Python image from the Docker Hub
#FROM python:3
FROM artifactory.luigi.worldpay.io/docker/python:3

# Set the working directory in the container
WORKDIR /usr/src/app

# Copy the requirements file into the container
COPY requirements.txt ./

# Install the required packages
RUN pip install --no-cache-dir -r requirements.txt

# Copy the rest of the application code into the container
COPY . .

# Expose the port that the application will run on
EXPOSE 5001

# Define the command to run the application
CMD [ "python", "compute.py"]
