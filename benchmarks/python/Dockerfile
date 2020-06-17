FROM python:3.6.5

RUN pip install awscli
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY preprocess.py .
ENTRYPOINT ["python", "-u"]
